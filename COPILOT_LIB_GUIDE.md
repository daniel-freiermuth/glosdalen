# Copilot Library - Feature Guide

## Overview
The Copilot library provides GitHub Copilot Chat integration for Android apps. Two main features are ready to use:

## 1. Authentication Flow (OAuth Device Flow)

### How it works
GitHub uses OAuth Device Flow for devices without a browser. The flow is:
1. Request a device code from GitHub
2. Show the user code to the user
3. Guide them to the verification URL (https://github.com/login/device)
4. User enters the code in their browser
5. Poll GitHub's API until authorization completes
6. Store the OAuth token securely

### Implementation

```kotlin
// Initialize the library
val copilot = CopilotChat.builder(context)
    .enableDebugLogging(true)
    .build()

// Step 1: Start authentication
suspend fun startAuth() {
    val result = copilot.authenticate()
    
    result.fold(
        onSuccess = { authResult ->
            // authResult is AuthResult.DeviceCodeRequired
            val userCode = authResult.userCode
            val verificationUri = authResult.verificationUri
            val expiresIn = authResult.expiresIn // seconds
            
            // Show to user:
            // "Go to: $verificationUri"
            // "Enter code: $userCode"
            // "Expires in: $expiresIn seconds"
            
            // Now start polling...
            completeAuth()
        },
        onFailure = { error ->
            // Handle error: CopilotException.AuthException or NetworkException
            Log.e("Auth", "Failed to start auth: ${error.message}")
        }
    )
}

// Step 2: Complete authentication by polling
suspend fun completeAuth() {
    val result = copilot.completeAuthentication()
    
    result.fold(
        onSuccess = { authResult ->
            when (authResult) {
                is AuthResult.Success -> {
                    // Successfully authenticated!
                    Log.i("Auth", "Authentication successful!")
                }
                is AuthResult.Failed -> {
                    // Auth failed
                    Log.e("Auth", "Auth failed: ${authResult.error.message}")
                }
                else -> {
                    // Should not happen in completeAuth
                }
            }
        },
        onFailure = { error ->
            // Network error or timeout
            Log.e("Auth", "Polling failed: ${error.message}")
        }
    )
}

// Alternative: Use automatic retry with timeout
suspend fun authenticateWithAutoRetry() {
    // First get device code
    val deviceCodeResult = copilot.authenticate()
    if (deviceCodeResult.isFailure) {
        return // Handle error
    }
    
    val deviceCode = deviceCodeResult.getOrThrow()
    // Show user code and URL to user...
    
    // This will automatically poll until success or timeout (5 minutes default)
    // Handles "authorization_pending" automatically
    val result = copilot.completeAuthentication()
    // ... handle result
}

// Check if already authenticated
suspend fun checkAuth() {
    val isAuth = copilot.isAuthenticated()
    if (isAuth) {
        Log.i("Auth", "Already authenticated!")
    } else {
        Log.i("Auth", "Need to authenticate")
    }
}

// Logout
suspend fun logout() {
    copilot.logout()
}
```

### UI Flow Example

```kotlin
// In your ViewModel
sealed class AuthState {
    object Idle : AuthState()
    data class ShowDeviceCode(
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int
    ) : AuthState()
    object Polling : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
val authState: StateFlow<AuthState> = _authState.asStateFlow()

fun startAuthentication() {
    viewModelScope.launch {
        val result = copilot.authenticate()
        
        result.fold(
            onSuccess = { deviceCode ->
                _authState.value = AuthState.ShowDeviceCode(
                    userCode = deviceCode.userCode,
                    verificationUri = deviceCode.verificationUri,
                    expiresIn = deviceCode.expiresIn
                )
                
                // Start polling
                pollForCompletion()
            },
            onFailure = { error ->
                _authState.value = AuthState.Error(error.message ?: "Auth failed")
            }
        )
    }
}

private fun pollForCompletion() {
    viewModelScope.launch {
        _authState.value = AuthState.Polling
        
        val result = copilot.completeAuthentication()
        
        result.fold(
            onSuccess = { authResult ->
                when (authResult) {
                    is AuthResult.Success -> {
                        _authState.value = AuthState.Success
                    }
                    is AuthResult.Failed -> {
                        _authState.value = AuthState.Error(
                            authResult.error.message ?: "Authentication failed"
                        )
                    }
                    else -> {}
                }
            },
            onFailure = { error ->
                _authState.value = AuthState.Error(error.message ?: "Polling failed")
            }
        )
    }
}
```

```kotlin
// In your Composable
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val authState by viewModel.authState.collectAsState()
    
    when (val state = authState) {
        is AuthState.Idle -> {
            Button(onClick = { viewModel.startAuthentication() }) {
                Text("Sign in with GitHub")
            }
        }
        
        is AuthState.ShowDeviceCode -> {
            Column {
                Text("Go to: ${state.verificationUri}")
                Text(
                    text = state.userCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text("Code expires in: ${state.expiresIn / 60} minutes")
                
                Button(onClick = { 
                    // Open browser
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.verificationUri))
                    context.startActivity(intent)
                }) {
                    Text("Open GitHub")
                }
            }
        }
        
        is AuthState.Polling -> {
            Column {
                CircularProgressIndicator()
                Text("Waiting for authorization...")
            }
        }
        
        is AuthState.Success -> {
            Text("✓ Successfully authenticated!")
        }
        
        is AuthState.Error -> {
            Column {
                Text("Error: ${state.message}")
                Button(onClick = { viewModel.startAuthentication() }) {
                    Text("Retry")
                }
            }
        }
    }
}
```

## 2. Query Flow (Chat with LLM)

### How it works
1. Library automatically manages Copilot tokens (exchanges OAuth token for Copilot access tokens)
2. Discovers available models (GPT-4o, Claude, Gemini, etc.)
3. Sends chat requests with chosen model
4. Returns AI responses

### Implementation

```kotlin
// Simple chat - uses default model (usually GPT-4o)
suspend fun sendSimpleMessage() {
    val result = copilot.chat("Explain quantum computing in simple terms")
    
    result.fold(
        onSuccess = { response ->
            Log.i("Chat", "Response: $response")
        },
        onFailure = { error ->
            Log.e("Chat", "Error: ${error.message}")
        }
    )
}

// Chat with specific model
suspend fun sendWithModel() {
    val result = copilot.chat(
        message = "Write a haiku about coding",
        modelId = "gpt-4o"
    )
    // ... handle result
}

// Get available models
suspend fun getModels() {
    val result = copilot.getModels()
    
    result.fold(
        onSuccess = { models ->
            models.forEach { model ->
                Log.i("Models", """
                    ID: ${model.id}
                    Name: ${model.name}
                    Version: ${model.version}
                    Is Free: ${model.isFree}
                    Max Tokens: ${model.capabilities?.limits?.maxOutputTokens}
                """.trimIndent())
            }
        },
        onFailure = { error ->
            Log.e("Models", "Failed to get models: ${error.message}")
        }
    )
}

// Get only free models (don't consume tokens)
suspend fun getFreeModels() {
    val result = copilot.getFreeModels()
    // Free models: gpt-4o, o3-mini, gpt-4.1, etc.
}

// Get recommended models (free + high quality)
suspend fun getRecommendedModels() {
    val result = copilot.getRecommendedModels()
}

// Advanced: Full control over request
suspend fun advancedChat() {
    val request = ChatRequest(
        messages = listOf(
            Message(role = "system", content = "You are a helpful coding assistant"),
            Message(role = "user", content = "How do I sort a list in Kotlin?")
        ),
        model = "gpt-4o",
        maxTokens = 500,
        temperature = 0.7,
        stream = false
    )
    
    val result = copilot.chat().sendChatRequest(request)
    
    result.fold(
        onSuccess = { response ->
            val answer = response.choices.firstOrNull()?.message?.content
            Log.i("Chat", "Answer: $answer")
        },
        onFailure = { error ->
            when (error) {
                is CopilotException.ModelException.ModelNotFound -> {
                    Log.e("Chat", "Model not available")
                }
                is CopilotException.ModelException.TokenLimitExceeded -> {
                    Log.e("Chat", "Too many tokens requested")
                }
                is CopilotException.NetworkException.RateLimited -> {
                    Log.e("Chat", "Rate limited, retry after ${error.retryAfter}s")
                }
                else -> {
                    Log.e("Chat", "Error: ${error.message}")
                }
            }
        }
    )
}
```

### UI Flow Example

```kotlin
// In your ViewModel
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val availableModels: List<CopilotModel> = emptyList(),
    val selectedModel: String? = null
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private val _uiState = MutableStateFlow(ChatUiState())
val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

init {
    loadModels()
}

private fun loadModels() {
    viewModelScope.launch {
        val result = copilot.getFreeModels() // Or getModels() for all
        
        result.fold(
            onSuccess = { models ->
                _uiState.update { it.copy(
                    availableModels = models,
                    selectedModel = models.firstOrNull()?.id
                )}
            },
            onFailure = { error ->
                _uiState.update { it.copy(error = "Failed to load models") }
            }
        )
    }
}

fun sendMessage(text: String) {
    if (text.isBlank()) return
    
    val userMessage = ChatMessage(text = text, isUser = true)
    
    _uiState.update { 
        it.copy(
            messages = it.messages + userMessage,
            isLoading = true,
            error = null
        )
    }
    
    viewModelScope.launch {
        val result = copilot.chat(
            message = text,
            modelId = _uiState.value.selectedModel
        )
        
        result.fold(
            onSuccess = { response ->
                val aiMessage = ChatMessage(text = response, isUser = false)
                _uiState.update { 
                    it.copy(
                        messages = it.messages + aiMessage,
                        isLoading = false
                    )
                }
            },
            onFailure = { error ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to send message"
                    )
                }
            }
        )
    }
}

fun selectModel(modelId: String) {
    _uiState.update { it.copy(selectedModel = modelId) }
}
```

```kotlin
// In your Composable
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Model selector
        ModelSelector(
            models = uiState.availableModels,
            selectedModel = uiState.selectedModel,
            onModelSelected = viewModel::selectModel
        )
        
        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(uiState.messages) { message ->
                ChatMessageItem(message)
            }
            
            if (uiState.isLoading) {
                item {
                    CircularProgressIndicator()
                }
            }
        }
        
        // Error
        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        // Input
        Row {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Copilot...") }
            )
            
            IconButton(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                enabled = inputText.isNotBlank() && !uiState.isLoading
            ) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}

@Composable
fun ModelSelector(
    models: List<CopilotModel>,
    selectedModel: String?,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = models.find { it.id == selectedModel }?.name ?: "Select model",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { 
                        Column {
                            Text(model.name)
                            Text(
                                text = if (model.isFree) "Free" else "Paid",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
```

## Key Model IDs

Free models (recommended):
- `gpt-4o` - GPT-4 Omni (default, best balance)
- `o3-mini` - Fast, efficient
- `gpt-4.1` - Latest GPT-4

Paid models (consume tokens):
- `claude-3-7-sonnet` - Anthropic Claude
- `gemini-2-flash-thinking` - Google Gemini
- `grok-3` - xAI Grok

## Error Handling

Common exceptions to handle:

```kotlin
when (error) {
    // Auth errors
    is CopilotException.AuthException.TokenExpired -> {
        // Re-authenticate
    }
    is CopilotException.AuthException.InvalidToken -> {
        // Clear and re-auth
    }
    
    // Network errors
    is CopilotException.NetworkException.NoConnection -> {
        // Show "No internet" message
    }
    is CopilotException.NetworkException.RateLimited -> {
        // Wait and retry: error.retryAfter seconds
    }
    is CopilotException.NetworkException.Timeout -> {
        // Retry request
    }
    
    // Model errors
    is CopilotException.ModelException.ModelNotFound -> {
        // Model unavailable, use fallback
    }
    is CopilotException.ModelException.TokenLimitExceeded -> {
        // Reduce maxTokens or split request
    }
    is CopilotException.ModelException.QuotaExceeded -> {
        // User out of quota, show message
    }
    
    // Chat errors
    is CopilotException.ChatException.EmptyMessage -> {
        // Validate input
    }
    is CopilotException.ChatException.ContentFiltered -> {
        // Message violated content policy
    }
    
    else -> {
        // Generic error handling
    }
}
```

## Complete Integration Example

```kotlin
// In your Application class or DI setup
class MyApp : Application() {
    lateinit var copilot: CopilotChat
    
    override fun onCreate() {
        super.onCreate()
        
        copilot = CopilotChat.builder(this)
            .enableDebugLogging(BuildConfig.DEBUG)
            .setConnectionTimeout(30)
            .setReadTimeout(60)
            .build()
    }
}

// In your ViewModel with Hilt
@HiltViewModel
class CopilotViewModel @Inject constructor(
    application: Application
) : ViewModel() {
    
    private val copilot = (application as MyApp).copilot
    
    fun checkAuthAndChat() {
        viewModelScope.launch {
            // 1. Check if authenticated
            if (!copilot.isAuthenticated()) {
                // Start auth flow
                startAuthentication()
                return@launch
            }
            
            // 2. Get models
            val modelsResult = copilot.getFreeModels()
            if (modelsResult.isFailure) {
                // Handle error
                return@launch
            }
            
            // 3. Send chat
            val chatResult = copilot.chat("Hello!")
            chatResult.fold(
                onSuccess = { response ->
                    Log.i("Copilot", "Response: $response")
                },
                onFailure = { error ->
                    Log.e("Copilot", "Error: ${error.message}")
                }
            )
        }
    }
    
    private suspend fun startAuthentication() {
        // Implement auth flow as shown above
    }
}
```

## Tips

1. **Always check authentication first**: Call `isAuthenticated()` before making requests
2. **Use free models**: Free models don't consume Copilot tokens (gpt-4o, o3-mini, gpt-4.1)
3. **Handle rate limits**: Implement exponential backoff for rate limit errors
4. **Store model selection**: Let users pick their preferred model and save it
5. **Validate input**: Check message length before sending
6. **Timeout handling**: Set appropriate timeouts for your use case
7. **Error messages**: Provide user-friendly error messages, not raw exceptions

## Storage

The library automatically:
- Encrypts and stores OAuth tokens using EncryptedSharedPreferences
- Manages Copilot token lifecycle
- Caches model information
- All data is cleared on logout

No manual storage handling needed!
