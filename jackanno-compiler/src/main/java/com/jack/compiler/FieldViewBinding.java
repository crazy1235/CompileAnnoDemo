package com.jack.compiler;

import javax.lang.model.type.TypeMirror;

/**
 * Created by Admin on 2016/7/9.
 */

public class FieldViewBinding {

    private int resId;
    private String name;
    private TypeMirror typeMirror;

    public FieldViewBinding(int resId, String name, TypeMirror typeMirror) {
        this.resId = resId;
        this.typeMirror = typeMirror;
        this.name = name;
    }

    public int getResId() {
        return resId;
    }

    public void setResId(int resId) {
        this.resId = resId;
    }

    public TypeMirror getTypeMirror() {
        return typeMirror;
    }

    public void setTypeMirror(TypeMirror typeMirror) {
        this.typeMirror = typeMirror;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
