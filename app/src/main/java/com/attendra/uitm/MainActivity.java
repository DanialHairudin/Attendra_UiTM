package com.attendra.uitm;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setTextSize(20);
        text.setPadding(48, 160, 48, 48);
        setContentView(text);

        String projectId = FirebaseApp.getInstance().getOptions().getProjectId();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        text.setText("Attendra UiTM\n\nFirebase connected ✓\nProject: " + projectId
                + "\n\n" + (user == null ? "No user logged in" : "Logged in: " + user.getEmail()));
    }
}