attribute vec4 a_Position;
attribute vec2 a_ImgCoord;

varying vec2 v_ImgCoord;

void main() {
   gl_Position = a_Position;
   v_ImgCoord = a_ImgCoord;
}
