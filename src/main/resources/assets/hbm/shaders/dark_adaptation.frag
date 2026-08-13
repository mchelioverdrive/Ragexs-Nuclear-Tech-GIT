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
    float lifted = base + (sqrt(base) - base) * effect * 0.28 * (1.0 - centralPenalty);
    color *= lifted / max(base, 0.001);

    // Cleared depth is exactly one. The narrow transition retains distant geometry despite nonlinear depth precision.
    float d = texture2D(depthSource, uv).r;
    float geometry = float(hasDepth) * (1.0 - smoothstep(0.9999990, 0.9999999, d));
    float centerDepth = linearDepth(d);
    float leftDepth = linearDepth(texture2D(depthSource, uv - vec2(texel.x, 0.0)).r);
    float rightDepth = linearDepth(texture2D(depthSource, uv + vec2(texel.x, 0.0)).r);
    float downDepth = linearDepth(texture2D(depthSource, uv - vec2(0.0, texel.y)).r);
    float upDepth = linearDepth(texture2D(depthSource, uv + vec2(0.0, texel.y)).r);
    float relativeGradient = (abs(leftDepth - rightDepth) + abs(downDepth - upDepth)) / max(centerDepth, 1.0);
    float depthShape = smoothstep(0.015, 0.20, relativeGradient);
    float shapeModulation = mix(1.0, 1.20, depthShape);
    float blackBlend = 1.0 - smoothstep(0.0, 0.012, l);
    float perceivedAmbient = pow(clamp(ambientScotopic, 0.0, 1.0), 0.30);
    float eyeRecovery = clamp(coneAdaptation * 0.25 + rodAdaptation * 0.75, 0.0, 1.0);
    // Legacy 0.055 now describes the base of a bounded perceptual target (0.140 at the default).
    float targetLuminance = clamp(scotopicFloor * 2.55, 0.0, 0.30);
    float recoveryStrength = clamp(strength, 0.0, 1.0);
    float blackRecovery = geometry * blackBlend * perceivedAmbient * eyeRecovery * targetLuminance *
        shapeModulation * (1.0 - centralPenalty) * recoveryStrength;

    if(debugView == 1) { gl_FragColor = vec4(vec3(geometry), 1.0); return; }
    if(debugView == 2) { gl_FragColor = vec4(vec3(blackRecovery), 1.0); return; }

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
