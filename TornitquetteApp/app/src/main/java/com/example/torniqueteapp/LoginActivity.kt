package com.example.torniqueteapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance() // Inicializa Realtime Database
        initViews()
        checkCurrentUser()
        setupLoginButton()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun checkCurrentUser() {
        if (auth.currentUser != null) {
            checkUserInTourniquetsTable(auth.currentUser?.uid) // Verifica si está en la tabla
        }
    }

    private fun setupLoginButton() {
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                attemptLogin(email, password)
            }
        }
    }

    private fun attemptLogin(email: String, password: String) {
        showLoadingState()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result?.user?.uid
                    if (userId != null) {
                        checkUserInTourniquetsTable(userId) // Verifica en la tabla después del login
                    } else {
                        showStatusMessage("Error al obtener ID de usuario", R.color.red)
                        hideLoadingState()
                    }
                } else {
                    handleLoginError(task.exception)
                    hideLoadingState()
                }
            }
    }

    private fun checkUserInTourniquetsTable(userId: String?) {
        if (userId == null) {
            showStatusMessage("Usuario no válido", R.color.red)
            auth.signOut() // Cierra sesión si no hay UID
            hideLoadingState()
            return
        }

        val tourniquetsRef = database.getReference("tourniquets") // Ajusta el nombre de la tabla
        tourniquetsRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    handleLoginSuccess() // Usuario está en la tabla, permite acceso
                } else {
                    showStatusMessage("No tienes acceso al torniquete", R.color.red)
                    auth.signOut() // Cierra sesión si no está en la tabla
                }
                hideLoadingState()
            }

            override fun onCancelled(error: DatabaseError) {
                showStatusMessage("Error de base de datos", R.color.red)
                hideLoadingState()
            }
        })
    }

    private fun showLoadingState() {
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false
        tvStatus.text = "Verificando acceso..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.blue))
    }

    private fun hideLoadingState() {
        progressBar.visibility = View.GONE
        btnLogin.isEnabled = true
    }

    private fun handleLoginSuccess() {
        showStatusMessage("¡Acceso permitido!", R.color.green)
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToQrScanner()
        }, 1000)
    }

    private fun handleLoginError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> "Usuario no registrado"
            is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
            else -> "Error al iniciar sesión. Intente nuevamente"
        }
        showStatusMessage(errorMessage, R.color.red)
    }

    private fun navigateToQrScanner() {
        startActivity(Intent(this, QrScannerActivity::class.java))
        finish()
    }

    private fun showStatusMessage(message: String, colorRes: Int) {
        tvStatus.text = message
        tvStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            showInputError(etEmail, "El correo electrónico es requerido")
            isValid = false
        }

        if (password.isEmpty()) {
            showInputError(etPassword, "La contraseña es requerida")
            isValid = false
        }

        return isValid
    }

    private fun showInputError(field: TextInputEditText, message: String) {
        field.error = message
        showStatusMessage(message, R.color.red)
    }
}