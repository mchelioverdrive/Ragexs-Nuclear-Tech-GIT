#version 120
uniform sampler2D source;
uniform sampler2D depthSource;
uniform vec2 texel;
uniform float adaptation;
uniform float rodAdaptation;
uniform float strength;
uniform float noiseAmount;
uniform float centerLoss;
uniform float time;
uniform int quality;
uniform int hasDepth;

float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233)) + floor(time * 31.0)) * 43758.5453); }

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec3 original = texture2D(source, uv).rgb;
    float l = lum(original);
    float shadow = 1.0 - smoothstep(0.075, 0.42, l);
    float radial = 1.0 - smoothstep(0.0, 0.48, length(uv - vec2(0.5)));
    float centralPenalty = radial * centerLoss * rodAdaptation;
    float effect = shadow * adaptation * strength;

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
    vec3 color = mix(original, blur, effect * rodAdaptation * (0.30 + centralPenalty * 0.45));
    float base = max(lum(color), 0.0);
    float lifted = base + (sqrt(base) - base) * effect * 0.28 * (1.0 - centralPenalty);
    color *= lifted / max(base, 0.001);

    // Depth is the only signal Hardcore Darkness cannot erase. Nearby valid geometry gets a
    // tiny, achromatic edge/distance cue; depth-clear sky and void remain exactly black.
    float d = texture2D(depthSource, uv).r;
    float dx = abs(d - texture2D(depthSource, uv + vec2(texel.x, 0.0)).r) + abs(d - texture2D(depthSource, uv - vec2(texel.x, 0.0)).r);
    float dy = abs(d - texture2D(depthSource, uv + vec2(0.0, texel.y)).r) + abs(d - texture2D(depthSource, uv - vec2(0.0, texel.y)).r);
    float geometry = float(hasDepth) * (1.0 - smoothstep(0.9985, 0.9999, d));
    float depthShape = clamp((dx + dy) * 42.0 + (1.0 - d) * 6.0, 0.0, 1.0);
    float blackBlend = 1.0 - smoothstep(0.0, 0.012, l);
    float blackRecovery = geometry * depthShape * blackBlend * rodAdaptation * strength * 0.024 * (1.0 - centralPenalty);
    color = max(color, vec3(blackRecovery));
    vec3 gray = vec3(lum(color));
    color = mix(color, gray, effect * rodAdaptation * 0.88);
    color = mix(vec3(lum(color)), color, 1.0 - effect * rodAdaptation * 0.20); // shadow contrast compression
    float signalLoss = 1.0 - smoothstep(0.0, 0.06, l);
    float noise = (hash(gl_FragCoord.xy) - 0.5) * noiseAmount * shadow * (0.35 + signalLoss * 0.65) * strength;
    color += vec3(noise) * max(smoothstep(0.0, 0.006, l), geometry);
    gl_FragColor = vec4(max(mix(original, color, shadow), vec3(0.0)), 1.0);
}
