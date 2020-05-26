package com.kazuki.replaceobject.replaceobject;

import android.util.Log;

public class ObjectList {
    private String objName = "person";
    private String previousObjName="person";
    private String objFile="models/andy.obj";
    private String texFile="models/andy.png";

    public void setObjName(String objName) {
        this.objName = objName;
    }

    public String getObjFile() {
        return objFile;
    }

    public String getTexFile() {
        return texFile;
    }

    /** if current frame object is not the same as the previous frame, model and texture is changed**/
    public boolean isReplace() {
        if(objName==previousObjName){
            return false;
        }
        else{
            switch (objName){
                case "chair":
                    previousObjName=objName;
                    objFile="models/chair.obj";
                    texFile="models/chair.png";
                    break;
                case  "tv":
                    previousObjName=objName;
                    objFile="models/monitor.obj";
                    texFile="models/monitor.png";
                    break;
                default:
                    previousObjName=objName;
                    objFile="models/andy.obj";
                    texFile="models/andy.png";
            }
            return true;
        }
    }


}
