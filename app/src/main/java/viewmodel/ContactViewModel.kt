package com.guardianlink.app.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import com.guardianlink.app.data.ContactLocalStore
import com.guardianlink.app.data.ContactRepository
import com.guardianlink.app.model.Contact

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContactRepository()
    private val currentUid: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "demo_user"

    // Compose observes this list directly
    val contacts = mutableStateListOf<Contact>()

    init {
        // 1. Immediately load all locally saved contacts from SharedPreferences
        val cached = ContactLocalStore.loadContacts(getApplication())
        if (cached.isNotEmpty()) {
            contacts.clear()
            contacts.addAll(cached)
        }

        // 2. Fetch live contacts from backend
        repository.listenToContacts(currentUid) { updatedList ->
            if (updatedList.isNotEmpty()) {
                val merged = updatedList.toMutableList()
                for (local in contacts) {
                    if (merged.none { it.phone == local.phone }) {
                        merged.add(local)
                    }
                }
                contacts.clear()
                contacts.addAll(merged)
                ContactLocalStore.saveContacts(getApplication(), merged)
            }
        }
    }

    fun addContact(name: String, phone: String) {
        val contact = Contact(name = name, phone = phone)
        // 1. Update UI list immediately
        contacts.removeAll { it.phone == phone }
        contacts.add(contact)

        // 2. Persist to disk immediately
        ContactLocalStore.saveContacts(getApplication(), contacts.toList())

        // 3. Sync with backend
        repository.saveContact(currentUid, contact)
    }

    fun deleteContact(contact: Contact) {
        // 1. Remove from UI list immediately
        contacts.removeAll { it.phone == contact.phone }

        // 2. Persist to disk immediately
        ContactLocalStore.saveContacts(getApplication(), contacts.toList())

        // 3. Sync with backend
        repository.deleteContact(currentUid, contact)
    }

    fun updateContact(oldContact: Contact, newName: String, newPhone: String) {
        val updated = Contact(name = newName, phone = newPhone)
        val idx = contacts.indexOfFirst { it.phone == oldContact.phone }
        if (idx != -1) {
            contacts[idx] = updated
        } else {
            contacts.add(updated)
        }

        ContactLocalStore.saveContacts(getApplication(), contacts.toList())

        if (oldContact.phone != newPhone) {
            repository.deleteContact(currentUid, oldContact)
        }
        repository.saveContact(currentUid, updated)
    }
}