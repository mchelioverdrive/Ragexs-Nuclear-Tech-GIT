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

    float perceivedAmbient = pow(clamp(ambientScotopic, 0.0, 1.0), 0.30);
    float eyeRecovery = clamp(coneAdaptation * 0.25 + rodAdaptation * 0.75, 0.0, 1.0);
    float recoveryStrength = clamp(strength, 0.0, 1.0);
    float targetLuminance = clamp(scotopicFloor * 2.55, 0.0, 0.30);

    // A cleared 24-bit depth sample is exactly 1.0; the immediately preceding representable
    // value is real far geometry and must remain usable.
    float d = texture2D(depthSource, uv).r;
    float geometry = float(hasDepth) * (1.0 - step(1.0, d));
    float centerDepth = linearDepth(d);

    float scotopicSignal = clamp(perceivedAmbient * eyeRecovery, 0.0, 1.0);
    // NEAR: best coarse perception, nominally ending between 3.5 and 5 blocks.
    float nearEnd = mix(3.5, 5.0, scotopicSignal);
    // MID: progressively reduced contrast and detail, ending between 12 and 20 blocks.
    float midEnd = mix(12.0, 20.0, scotopicSignal);
    // FAR: silhouettes remain useful out to between 28 and 55 blocks.
    float farEnd = mix(28.0, 55.0, scotopicSignal);
    // FADE: recovered scotopic visibility reaches darkness between 38 and 70 blocks.
    float fadeEnd = mix(38.0, 70.0, scotopicSignal);

    float nearProgress = smoothstep(0.0, nearEnd, centerDepth);
    float midProgress = smoothstep(nearEnd, midEnd, centerDepth);
    float farProgress = smoothstep(midEnd, farEnd, centerDepth);
    float fadeProgress = smoothstep(farEnd, fadeEnd, centerDepth);
    float nearFactor = 1.0 - smoothstep(nearEnd * 0.65, nearEnd, centerDepth);
    float midFactor = smoothstep(nearEnd * 0.65, nearEnd, centerDepth) *
        (1.0 - smoothstep(midEnd * 0.75, midEnd, centerDepth));
    float farFactor = smoothstep(midEnd * 0.70, midEnd, centerDepth) * (1.0 - fadeProgress);
    float factorTotal = max(nearFactor + midFactor + farFactor, 0.0001);
    nearFactor /= factorTotal;
    midFactor /= factorTotal;
    farFactor /= factorTotal;
    float visibilityDistance = 1.0 - fadeProgress;
    float distanceDetailLoss = clamp(nearProgress * 0.25 + midProgress * 0.45 +
        farProgress * 0.30, 0.0, 1.0);

    // The existing sample count is retained; only its radius grows continuously with distance.
    float blurRadius = mix(1.0, 1.4, nearProgress);
    blurRadius = mix(blurRadius, 2.5, midProgress);
    blurRadius = mix(blurRadius, 4.0, farProgress);
    vec2 blurTexel = texel * blurRadius;
    vec3 blur = original;
    if(quality > 0) {
        vec2 offsetX = vec2(blurTexel.x, 0.0);
        vec2 offsetY = vec2(0.0, blurTexel.y);
        float leftWeight = 1.0 - smoothstep(0.08, 0.45,
            abs(linearDepth(texture2D(depthSource, uv - offsetX).r) - centerDepth) / max(centerDepth, 1.0));
        float rightWeight = 1.0 - smoothstep(0.08, 0.45,
            abs(linearDepth(texture2D(depthSource, uv + offsetX).r) - centerDepth) / max(centerDepth, 1.0));
        float downWeight = 1.0 - smoothstep(0.08, 0.45,
            abs(linearDepth(texture2D(depthSource, uv - offsetY).r) - centerDepth) / max(centerDepth, 1.0));
        float upWeight = 1.0 - smoothstep(0.08, 0.45,
            abs(linearDepth(texture2D(depthSource, uv + offsetY).r) - centerDepth) / max(centerDepth, 1.0));
        blur = (original * 4.0 + texture2D(source, uv - offsetX).rgb * leftWeight +
            texture2D(source, uv + offsetX).rgb * rightWeight + texture2D(source, uv - offsetY).rgb * downWeight +
            texture2D(source, uv + offsetY).rgb * upWeight) / max(4.0 + leftWeight + rightWeight + downWeight + upWeight, 0.001);
        if(quality > 1) {
            float diagonalA = 1.0 - smoothstep(0.08, 0.45,
                abs(linearDepth(texture2D(depthSource, uv + blurTexel).r) - centerDepth) / max(centerDepth, 1.0));
            float diagonalB = 1.0 - smoothstep(0.08, 0.45,
                abs(linearDepth(texture2D(depthSource, uv - blurTexel).r) - centerDepth) / max(centerDepth, 1.0));
            vec2 diagonalCOffset = vec2(blurTexel.x, -blurTexel.y);
            vec2 diagonalDOffset = vec2(-blurTexel.x, blurTexel.y);
            float diagonalC = 1.0 - smoothstep(0.08, 0.45,
                abs(linearDepth(texture2D(depthSource, uv + diagonalCOffset).r) - centerDepth) / max(centerDepth, 1.0));
            float diagonalD = 1.0 - smoothstep(0.08, 0.45,
                abs(linearDepth(texture2D(depthSource, uv + diagonalDOffset).r) - centerDepth) / max(centerDepth, 1.0));
            blur = (blur * 8.0 + texture2D(source, uv + blurTexel).rgb * diagonalA +
                texture2D(source, uv - blurTexel).rgb * diagonalB +
                texture2D(source, uv + diagonalCOffset).rgb * diagonalC +
                texture2D(source, uv + diagonalDOffset).rgb * diagonalD) /
                max(8.0 + diagonalA + diagonalB + diagonalC + diagonalD, 0.001);
        }
    }
    float blurredLum = lum(blur);
    float rodEffect = clamp(effect * rodAdaptation, 0.0, 1.0);

    float nearBoost = 0.30 * geometry * nearFactor * scotopicSignal * recoveryStrength;
    float recoveredLum = recoverLowLightLuminance(sourceLum, perceivedAmbient, eyeRecovery,
        recoveryStrength, targetLuminance, nearBoost, centralPenalty);
    float recoveredBlurredLum = recoverLowLightLuminance(blurredLum, perceivedAmbient, eyeRecovery,
        recoveryStrength, targetLuminance, nearBoost, centralPenalty);

    float regionRecovery = nearFactor + midFactor * mix(0.85, 0.60, midProgress) +
        farFactor * mix(0.60, 0.28, farProgress);

    // Rod vision loses fine local contrast and spatial acuity. The center receives up to
    // another 15%, without changing the radial brightness enough to form a vignette.
    float distanceAcuity = mix(0.25, 0.40, nearProgress);
    distanceAcuity = mix(distanceAcuity, 0.65, midProgress);
    distanceAcuity = mix(distanceAcuity, 0.82, farProgress);
    distanceAcuity = max(distanceAcuity, mix(0.25, 0.82, distanceDetailLoss));
    float acuityLoss = clamp(rodEffect * max(0.40 + centralPenalty * 0.75,
        distanceAcuity), 0.0, 0.85);
    if(quality > 0) recoveredLum = mix(recoveredLum, recoveredBlurredLum, acuityLoss);

    float detailRetention = mix(0.78, 0.70, nearProgress);
    detailRetention = mix(detailRetention, 0.50, midProgress);
    detailRetention = mix(detailRetention, 0.25, farProgress);
    float localMean = recoveredBlurredLum;
    float localDetail = recoveredLum - localMean;
    recoveredLum = localMean + localDetail * mix(1.0, detailRetention, rodEffect);

    // Distance attenuates only recovered shadow signal; the original framebuffer, including
    // distant photopic emitters, is never multiplied by the visibility envelope.
    float rangeRecovery = mix(1.0, visibilityDistance, geometry * shadow);
    float recoveryEnvelope = clamp(rangeRecovery * mix(1.0, regionRecovery, geometry), 0.0, 1.0);
    recoveredLum = sourceLum + (recoveredLum - sourceLum) * recoveryEnvelope;

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
        shapeModulation * (1.0 - centralPenalty) * recoveryStrength * visibilityDistance * regionRecovery;

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
