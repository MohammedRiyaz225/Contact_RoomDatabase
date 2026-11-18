package com.example.cse226

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String
)

@Dao
interface ContactDao {

    @Query("""
        SELECT * FROM contacts
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchContacts(query: String): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getCount(): Int
}

@Database(entities = [Contact::class], version = 1)
abstract class ContactDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}

class ContactViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val dao: ContactDao
) : ViewModel() {

    val searchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow("search_query", "")

    fun updateSearchQuery(query: String) {
        savedStateHandle["search_query"] = query
    }

    fun addContact(name: String, phone: String) {
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch {
            dao.insert(Contact(name = name.trim(), phone = phone.trim()))
        }
    }

    init {
        // Seed initial sample contacts if database is empty
        viewModelScope.launch {
            if (dao.getCount() == 0) {
                dao.insert(Contact(name = "Alice Smith", phone = "+1 555-0192"))
                dao.insert(Contact(name = "Bob Jones", phone = "+1 555-0143"))
                dao.insert(Contact(name = "Charlie Brown", phone = "+1 555-0176"))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val contacts: StateFlow<List<Contact>> =
        searchQuery
            .flatMapLatest { query ->
                dao.searchContacts(query)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )
}

class ContactViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val dao: ContactDao
) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        if (modelClass.isAssignableFrom(ContactViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactViewModel(handle, dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}

class RoomDatabase2 : ComponentActivity() {
    private lateinit var database: ContactDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = Room.databaseBuilder(
            applicationContext,
            ContactDatabase::class.java,
            "contact_database"
        ).build()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val factory = ContactViewModelFactory(
                        this,
                        database.contactDao()
                    )
                    val viewModel: ContactViewModel = viewModel(
                        factory = factory
                    )
                    ContactScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ContactScreen(viewModel: ContactViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Contact Manager",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Search Contacts") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Add Contact Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Add New Contact",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.addContact(newName, newPhone)
                            newName = ""
                            newPhone = ""
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Add Contact")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Contacts List (${contacts.size})",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isEmpty()) "No contacts stored yet." else "No contacts match '$query'",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts) { contact ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = contact.phone,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
