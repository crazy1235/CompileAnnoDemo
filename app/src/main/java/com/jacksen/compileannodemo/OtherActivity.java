package com.jacksen.compileannodemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Switch;

import com.jack.anno.BindView;
import com.jacksen.jackanno.JackAnno;

public class OtherActivity extends AppCompatActivity {

    @BindView(R.id.button)
    Button btn;

    @BindView(R.id.switch1)
    Switch aSwitch;

    @BindView(R.id.checkBox)
    CheckBox checkBox;

    @BindView(R.id.radioButton)
    RadioButton rbtn1;

    @BindView(R.id.radioButton2)
    RadioButton rbtn2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);

        JackAnno.bind(this);

        btn.setText("sololo");
    }
}
