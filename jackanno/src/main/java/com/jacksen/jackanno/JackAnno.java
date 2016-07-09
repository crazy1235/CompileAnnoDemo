package com.jacksen.jackanno;

import android.app.Activity;

/**
 * Created by Admin on 2016/7/9.
 */

public class JackAnno {

    public static void bind(Activity activity){
        String className = activity.getClass().getName();
        try {
            Class<?> viewBindingClass = Class.forName(className + "$$ViewBinder");
            ViewBinder viewBinder = (ViewBinder) viewBindingClass.newInstance();
            viewBinder.bind(activity);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
