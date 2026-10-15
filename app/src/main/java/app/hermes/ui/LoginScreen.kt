package app.hermes.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hermes.R
import app.hermes.ui.theme.NousBlue
import app.hermes.ui.theme.NousSnow
import app.hermes.ui.theme.NousWhite

@Composable
fun LoginScreen(
    onLogin: (url: String, username: String, password: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf(DevCredentials.GATEWAY_URL) }
    var username by remember { mutableStateOf(DevCredentials.USERNAME) }
    var password by remember { mutableStateOf(DevCredentials.PASSWORD) }

    // Website hero: solid Nous blue + light type + white CTA
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NousBlue),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.hermes_mark),
                contentDescription = "Hermes",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit,
            )

            Text(
                text = "HERMES AGENT",
                color = NousWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "by Nous Research",
                color = NousSnow.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NousWhite,
                unfocusedTextColor = NousSnow,
                focusedBorderColor = NousWhite,
                unfocusedBorderColor = NousSnow.copy(alpha = 0.55f),
                focusedLabelColor = NousWhite,
                unfocusedLabelColor = NousSnow.copy(alpha = 0.8f),
                cursorColor = NousWhite,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Gateway URL") },
                placeholder = { Text("https://hermes.carter.software") },
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(0.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(0.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(0.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (!isLoading && url.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                        onLogin(url.trim().trimEnd('/'), username.trim(), password)
                    }
                }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = {
                    onLogin(url.trim().trimEnd('/'), username.trim(), password)
                },
                enabled = !isLoading &&
                    url.isNotBlank() &&
                    username.isNotBlank() &&
                    password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NousWhite,
                    contentColor = NousBlue,
                    disabledContainerColor = NousWhite.copy(alpha = 0.5f),
                    disabledContentColor = NousBlue.copy(alpha = 0.5f),
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = NousBlue,
                    )
                } else {
                    Text("CONNECT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Image(
                painter = painterResource(id = R.drawable.nous_logo),
                contentDescription = "Nous Research",
                modifier = Modifier
                    .height(28.dp)
                    .fillMaxWidth(0.45f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
