#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform vec2 circleCenter;
uniform float circleRadius;
uniform vec4 glowColor;
uniform float glowWidth;
uniform sampler2D u_texture;

void main() {
    vec4 textureColor = texture2D(u_texture, v_texCoords);
    float dist = distance(gl_FragCoord.xy, circleCenter);
    float glow = smoothstep(circleRadius, circleRadius - glowWidth, dist);
    vec4 glowEffect = mix(vec4(0.0), glowColor, glow);
    gl_FragColor = textureColor + glowEffect;
}
