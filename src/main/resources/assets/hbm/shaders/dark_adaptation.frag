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

// Recover only luminance here. Minecraft's night lightmap carries useful texture in RGB,
// but its blue-heavy ratio is not a color signal that dark-adapted rods should amplify.
float recoverLowLightLuminance(float sourceLum, float perceivedAmbient, float eyeRecovery,
        float recoveryStrength, float targetLuminance, float nearBoost, float centralPenalty) {
    float darknessWeight = 1.0 - smoothstep(0.02, 0.18, sourceLum);
    float scotopicGain = clamp(1.0 + 16.0 * perceivedAmbient * eyeRecovery *
        darknessWeight * recoveryStrength * (1.0 + nearBoost), 1.0, 18.0);
    float adaptedCeiling = max(sourceLum, perceivedAmbient * eyeRecovery * targetLuminance *
        (1.0 + nearBoost) * (1.0 - centralPenalty) * recoveryStrength);
    return max(sourceLum, min(sourceLum * scotopicGain, adaptedCeiling));
}

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec3 original = texture2D(source, uv).rgb;
    float sourceLum = lum(original);
    float shadow = 1.0 - smoothstep(0.075, 0.42, sourceLum);
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
    float blurredLum = lum(blur);
    float rodEffect = clamp(effect * rodAdaptation, 0.0, 1.0);

    float perceivedAmbient = pow(clamp(ambientScotopic, 0.0, 1.0), 0.30);
    float eyeRecovery = clamp(coneAdaptation * 0.25 + rodAdaptation * 0.75, 0.0, 1.0);
    float recoveryStrength = clamp(strength, 0.0, 1.0);
    float targetLuminance = clamp(scotopicFloor * 2.55, 0.0, 0.30);

    // A cleared 24-bit depth sample is exactly 1.0; the immediately preceding representable
    // value is real far geometry and must remain usable.
    float d = texture2D(depthSource, uv).r;
    float geometry = float(hasDepth) * (1.0 - step(1.0, d));
    float centerDepth = linearDepth(d);
    float nearVision = geometry * (1.0 - smoothstep(2.5, 4.0, centerDepth));
    float nearBoost = 0.30 * nearVision * perceivedAmbient * eyeRecovery * recoveryStrength;
    float recoveredLum = recoverLowLightLuminance(sourceLum, perceivedAmbient, eyeRecovery,
        recoveryStrength, targetLuminance, nearBoost, centralPenalty);
    float recoveredBlurredLum = recoverLowLightLuminance(blurredLum, perceivedAmbient, eyeRecovery,
        recoveryStrength, targetLuminance, nearBoost, centralPenalty);

    // Rod vision loses fine local contrast and spatial acuity. The center receives up to
    // another 15%, without changing the radial brightness enough to form a vignette.
    float acuityLoss = clamp(rodEffect * (0.40 + centralPenalty * 0.75), 0.0, 0.55);
    if(quality > 0) recoveredLum = mix(recoveredLum, recoveredBlurredLum, acuityLoss);

    // Reconstruct RGB only after luminance recovery. Deep scotopic pixels are neutral;
    // mesopic pixels progressively regain source chroma and locally bright pixels keep it.
    vec3 recoveredColor = original * recoveredLum / max(sourceLum, 0.0001);
    vec3 neutralColor = vec3(recoveredLum);
    float mesopicColor = smoothstep(0.025, 0.18, sourceLum);
    float brightColor = smoothstep(0.18, 0.50, sourceLum);
    float rodColorAvailability = 1.0 - rodEffect * 0.95;
    float chromaRetention = clamp(max(mesopicColor * rodColorAvailability, brightColor), 0.0, 1.0);
    vec3 color = mix(neutralColor, recoveredColor, chromaRetention);

    float leftDepth = linearDepth(texture2D(depthSource, uv - vec2(texel.x, 0.0)).r);
    float rightDepth = linearDepth(texture2D(depthSource, uv + vec2(texel.x, 0.0)).r);
    float downDepth = linearDepth(texture2D(depthSource, uv - vec2(0.0, texel.y)).r);
    float upDepth = linearDepth(texture2D(depthSource, uv + vec2(0.0, texel.y)).r);
    float relativeGradient = (abs(leftDepth - rightDepth) + abs(downDepth - upDepth)) / max(centerDepth, 1.0);
    float depthShape = smoothstep(0.015, 0.20, relativeGradient);
    float shapeModulation = mix(1.0, 1.20, depthShape);
    // Retained lightmap RGB is the primary terrain signal. Depth recovery is reserved for
    // effectively mathematical black rather than flattening dim real texture information.
    float blackBlend = 1.0 - smoothstep(0.00005, 0.00015, sourceLum);
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
    float signalScarcity = 1.0 - smoothstep(0.005, 0.08, sourceLum);
    float noiseAmplitude = clamp(noiseAmount * rodEffect * signalScarcity, 0.0, 0.018);
    float noise = (hash(gl_FragCoord.xy) - 0.5) * noiseAmplitude;
    float visibleSignal = max(smoothstep(0.0, 0.006, sourceLum), geometry * perceivedAmbient * eyeRecovery);
    color += vec3(noise) * visibleSignal;
    gl_FragColor = vec4(max(mix(original, color, shadow), vec3(0.0)), 1.0);
}
