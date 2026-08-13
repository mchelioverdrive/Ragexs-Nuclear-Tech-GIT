#version 120
uniform sampler2D source;
uniform sampler2D depthSource;
uniform vec2 texel;
uniform float adaptation;
uniform float coneAdaptation;
uniform float rodAdaptation;
uniform float strength;
uniform float ambientScotopic;
uniform float scotopicFloor;
uniform float noiseAmount;
uniform float centerLoss;
uniform float time;
uniform float nearPlane;
uniform float farPlane;
uniform int quality;
uniform int hasDepth;
uniform int debugView;

float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233)) + floor(time * 31.0)) * 43758.5453); }
float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / max(farPlane + nearPlane - z * (farPlane - nearPlane), 0.00001);
}

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec3 original = texture2D(source, uv).rgb;
    float l = lum(original);
    float shadow = 1.0 - smoothstep(0.075, 0.42, l);
    float radial = 1.0 - smoothstep(0.0, 0.48, length(uv - vec2(0.5)));
    float centralPenalty = clamp(radial * centerLoss * rodAdaptation, 0.0, 0.20);
    float effect = clamp(shadow * adaptation * strength, 0.0, 1.0);

    vec3 blur = original;
    if(quality > 0) {
        blur = (original * 4.0 + texture2D(source, uv + vec2(texel.x, 0.0)).rgb +
            texture2D(source, uv - vec2(texel.x, 0.0)).rgb + texture2D(source, uv + vec2(0.0, texel.y)).rgb +
            texture2D(source, uv - vec2(0.0, texel.y)).rgb) / 8.0;
        if(quality > 1) {
            blur = (blur * 8.0 + texture2D(source, uv + texel).rgb + texture2D(source, uv - texel).rgb +
                texture2D(source, uv + vec2(texel.x, -texel.y)).rgb + texture2D(source, uv + vec2(-texel.x, texel.y)).rgb) / 12.0;
        }
    }
    float rodEffect = clamp(effect * rodAdaptation, 0.0, 1.0);
    vec3 color = mix(original, blur, clamp(rodEffect * (0.30 + centralPenalty * 0.45), 0.0, 1.0));
    float base = max(lum(color), 0.0);

    float perceivedAmbient = pow(clamp(ambientScotopic, 0.0, 1.0), 0.30);
    float eyeRecovery = clamp(coneAdaptation * 0.25 + rodAdaptation * 0.75, 0.0, 1.0);
    float recoveryStrength = clamp(strength, 0.0, 1.0);
    float targetLuminance = clamp(scotopicFloor * 2.55, 0.0, 0.30);
    float adaptedTarget = perceivedAmbient * eyeRecovery * targetLuminance *
        (1.0 - centralPenalty) * recoveryStrength;

    // A cleared 24-bit depth sample is exactly 1.0; the immediately preceding representable
    // value is real far geometry and must remain usable.
    float d = texture2D(depthSource, uv).r;
    float geometry = float(hasDepth) * (1.0 - step(1.0, d));
    float centerDepth = linearDepth(d);
    float nearVision = geometry * (1.0 - smoothstep(2.5, 4.0, centerDepth));
    float nearBoost = 0.30 * nearVision * perceivedAmbient * eyeRecovery * recoveryStrength;
    float darknessWeight = 1.0 - smoothstep(0.02, 0.18, base);
    float scotopicGain = clamp(1.0 + 16.0 * perceivedAmbient * eyeRecovery *
        darknessWeight * recoveryStrength * (1.0 + nearBoost), 1.0, 18.0);
    float amplifiedLum = base * scotopicGain;
    float adaptedCeiling = max(base, adaptedTarget * (1.0 + nearBoost));
    float recoveredLum = max(base, min(amplifiedLum, adaptedCeiling));
    if(base > 0.000001) color *= recoveredLum / base;

    float leftDepth = linearDepth(texture2D(depthSource, uv - vec2(texel.x, 0.0)).r);
    float rightDepth = linearDepth(texture2D(depthSource, uv + vec2(texel.x, 0.0)).r);
    float downDepth = linearDepth(texture2D(depthSource, uv - vec2(0.0, texel.y)).r);
    float upDepth = linearDepth(texture2D(depthSource, uv + vec2(0.0, texel.y)).r);
    float relativeGradient = (abs(leftDepth - rightDepth) + abs(downDepth - upDepth)) / max(centerDepth, 1.0);
    float depthShape = smoothstep(0.015, 0.20, relativeGradient);
    float shapeModulation = mix(1.0, 1.20, depthShape);
    // Retained lightmap RGB is the primary terrain signal. Depth recovery is reserved for
    // effectively mathematical black rather than flattening dim real texture information.
    float blackBlend = 1.0 - smoothstep(0.00005, 0.00015, l);
    float blackRecovery = geometry * blackBlend * perceivedAmbient * eyeRecovery * targetLuminance *
        shapeModulation * (1.0 - centralPenalty) * recoveryStrength;

    if(debugView == 1) { gl_FragColor = vec4(vec3(geometry), 1.0); return; }
    if(debugView == 2) { gl_FragColor = vec4(vec3(blackRecovery), 1.0); return; }
    if(debugView == 3) {
        // Logarithmic linear-depth remapping keeps nearby and distant surfaces distinguishable;
        // cleared sky/void remains black through the shared geometry mask.
        float inspectedDepth = 1.0 - clamp(log(1.0 + centerDepth) / log(1.0 + farPlane), 0.0, 1.0);
        gl_FragColor = vec4(vec3(geometry * (0.15 + inspectedDepth * 0.85)), 1.0);
        return;
    }

    color = max(color, vec3(blackRecovery));
    vec3 gray = vec3(lum(color));
    color = mix(color, gray, clamp(rodEffect * 0.88, 0.0, 1.0));
    color = mix(vec3(lum(color)), color, clamp(1.0 - rodEffect * 0.20, 0.0, 1.0));
    float signalLoss = 1.0 - smoothstep(0.0, 0.06, l);
    float noiseAmplitude = clamp(noiseAmount * effect, 0.0, 0.025) * min(max(lum(color), blackRecovery) / 0.05, 1.0);
    float noise = (hash(gl_FragCoord.xy) - 0.5) * noiseAmplitude * shadow * (0.35 + signalLoss * 0.65);
    float visibleSignal = max(smoothstep(0.0, 0.006, l), geometry * perceivedAmbient * eyeRecovery);
    color += vec3(noise) * visibleSignal;
    gl_FragColor = vec4(max(mix(original, color, shadow), vec3(0.0)), 1.0);
}
