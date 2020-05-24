#extension GL_OES_EGL_image_external : require

precision mediump float;
varying vec2 v_ImgCoord;
uniform sampler2D TexCpuImage;

void main() {
    gl_FragColor.xyz = texture2D(TexCpuImage, v_ImgCoord).rgb;
    gl_FragColor.a = 1.0;
}
