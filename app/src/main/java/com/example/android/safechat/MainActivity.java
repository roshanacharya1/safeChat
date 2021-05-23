package com.example.android.safechat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
// import these for encryption-----------
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
//---------------------------------------
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 1;
    private static final String TAG = "MainActivity";
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private ListView mMessageListView;
    private messageViewCreat mMessageViewCreat;
    private EditText mMessageEditText;
    private Button mSendButton;
    private ChildEventListener childEventListener;
    private String mUsername;
    private Button mEncryptButton;
    private Button mDecryptButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;
        mFirebaseDatabase=FirebaseDatabase.getInstance();
        firebaseAuth=FirebaseAuth.getInstance();
        mMessagesDatabaseReference=mFirebaseDatabase.getReference().child("messages");
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mMessageEditText = (EditText) findViewById(R.id.messageText);
        mSendButton = (Button) findViewById(R.id.sendButton);
        mEncryptButton = (Button) findViewById(R.id.encryptButton);
        mDecryptButton = (Button) findViewById(R.id.decryptButton);


        List<messageStruct> friendlyMessages = new ArrayList<>();
        mMessageViewCreat = new messageViewCreat(this, R.layout.message_layout, friendlyMessages);
        mMessageListView.setAdapter(mMessageViewCreat);


        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

       // Send Button Implementation
        mSendButton.setOnClickListener((View view) -> {
            messageStruct friendlyMessage = new messageStruct(mMessageEditText.getText().toString(), mUsername);
            //pushing text to firebase and clearing editText
            mMessagesDatabaseReference.push().setValue(friendlyMessage);
            mMessageEditText.setText("");
        });


        //encryption Button implementation
        mEncryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    String inptext = mMessageEditText.getText().toString();
                    String outputstring = encrypt(inptext, "Roshan");
                    mMessageEditText.setText(outputstring);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        //decryption Button Implementation
        mDecryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    String inptext = mMessageEditText.getText().toString();
                    String outputstring = decrypt(inptext, "Roshan");
                    mMessageEditText.setText(outputstring);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

//checking if user is logged in or not and doing what required
        authStateListener=new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user=firebaseAuth.getCurrentUser();
                if(user!=null){
                    onSignedInitialize(user.getDisplayName());
                }
                else{
                    onSignedOutCleanup();
                    //if not signed in implemeting Auth UI(Firebase)
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build()
                                    ))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RC_SIGN_IN)
        {
            if(resultCode==RESULT_CANCELED)
            {
                Toast.makeText(MainActivity.this,"sign in cancelled!",Toast.LENGTH_SHORT).show();
                finish();
            }
            else
            {
                Toast.makeText(MainActivity.this,"sign in Successfull!",Toast.LENGTH_SHORT).show();

            }


        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_layout, menu);
        return true;
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }

    }



    @Override
    protected void onResume() {
        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);
    }




    @Override
    protected void onPause() {
        super.onPause();
        firebaseAuth.removeAuthStateListener(authStateListener);
        detachDatabaseReadListener();
        mMessageViewCreat.clear();
    }



    private void onSignedInitialize(String username) {
        mUsername=username;
        if(childEventListener==null) {
        childEventListener=new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                messageStruct message =snapshot.getValue(messageStruct.class);
                try {
                    message.setText(decrypt(message.getText(),"Roshan"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mMessageViewCreat.add(message);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };
        mMessagesDatabaseReference.addChildEventListener(childEventListener);
    }}




    private void onSignedOutCleanup()
    {
        mUsername=ANONYMOUS;
        mMessageViewCreat.clear();
        detachDatabaseReadListener();
    }



    public void detachDatabaseReadListener() {
        if (childEventListener != null) {
            mMessagesDatabaseReference.removeEventListener(childEventListener);
            childEventListener=null;
        }


    }

  //Implementing Aes Encryption

    private String encrypt(String data, String password_text) throws Exception {
        SecretKeySpec key = generateKey(password_text);
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = c.doFinal(data.getBytes("UTF-8"));
        String encryptedvalue = Base64.encodeToString(encVal, Base64.DEFAULT);
         return encryptedvalue;

    }


   //Implementing AES Decryption

    private String decrypt(String data, String password_text) throws Exception {
        SecretKeySpec key = generateKey(password_text);
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedvalue = Base64.decode(data, Base64.DEFAULT);
        byte[] decvalue = c.doFinal(decodedvalue);
        String decryptedvalue = new String(decvalue, "UTF-8");
        return decryptedvalue;
    }

    //Generating key required for encryption and decryption

    private SecretKeySpec generateKey(String password) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = password.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        return secretKeySpec;
    }

}
