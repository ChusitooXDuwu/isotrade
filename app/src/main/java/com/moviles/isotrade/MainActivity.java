package com.moviles.isotrade;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    EditText username;
    EditText password;
    Button loginButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Enlaza el layout 'activity_main.xml'

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (username.getText().toString().equals("user") && password.getText().toString().equals("1234")) {
                    Toast.makeText(MainActivity.this, "Login exitoso", Toast.LENGTH_SHORT).show();
                    System.out.println("Login exitoso");
                } else {
                    // Si el usuario o la contraseña son incorrectos, se muestra un mensaje de error
                    Toast.makeText(MainActivity.this, "Login fallido", Toast.LENGTH_SHORT).show();
                    System.out.println("Login fallido");
                }
            }
        });


    }
}

