#version 120
uniform sampler2D source;
uniform vec2 texel;
uniform float adaptation;
uniform float rodAdaptation;
uniform float strength;
uniform float noiseAmount;
uniform float centerLoss;
uniform float time;
uniform int quality;

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
    float base = lum(color);
    // Curved recovery is proportional to existing signal: mathematical black remains black.
    float lifted = base + (sqrt(max(base, 0.0)) - base) * effect * 0.28 * (1.0 - centralPenalty);
    color *= lifted / max(base, 0.001);
    vec3 gray = vec3(lum(color));
    color = mix(color, gray, effect * rodAdaptation * 0.88);
    color = mix(vec3(lum(color)), color, 1.0 - effect * rodAdaptation * 0.20); // shadow contrast compression
    float signalLoss = 1.0 - smoothstep(0.0, 0.06, l);
    float noise = (hash(gl_FragCoord.xy) - 0.5) * noiseAmount * shadow * (0.35 + signalLoss * 0.65) * strength;
    color += vec3(noise);
    gl_FragColor = vec4(mix(original, color, shadow), 1.0);
}
