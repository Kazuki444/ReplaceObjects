# ReplaceObjects
物体を認識し、それと同じカテゴリの仮想オブジェクトで置換するプログラム

In this study, we developed a system that combines Generic object recognition and image processing with AR technology 
to visually remove real objects and display only virtual objects in the same category as the real objects. 
In this system, categories and regions to be removed get from generic object recognition.
An image which the object erased from by image processing use as a background of AR, and we set a virtual object 
which is the same category as the recognized category. This system operates on one smartphone.

# DEMO

[Demo Movie] (https://drive.google.com/file/d/1B1JkDjy7j7KKUa0GYYhShCCVEpKmGGqF/view?usp=sharing)

テレビ　→　椅子　→　テディベア　の順で置換している
テディベアのモデルはAndroid Robotで置換した

たまにAndroid Robotが出てくるのは  
一般物体認識で誤認識があるとAndroid Robotに置換されるようにしている

以下、デモで使ったもの  
MobileNet v3の [.tflite](https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md)  
Google Polyから[Android Robot](https://poly.google.com/view/9-bJ2cXrk8S)  
Google Polyから[chair](https://poly.google.com/view/7Jl72KgiRl-)  
Google Polyから[tv](https://poly.google.com/view/5qZ5IaClHHw)

#NOTE

UI
- タップ　 ・・・ 配置orタップした場所に移動
- ドラック ・・・ 回転
- ピンチ　 ・・・ 拡大縮小

正常に動作する環境
- 置換できる物体は１つ
- スマホの向きはPortrait限定
- MobileNetで認識できる物体は画面に１つしか映っていない
