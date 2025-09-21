package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.R

class MessageAdapter(private val messages: MutableList<String>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_BOT = 1
        private const val VIEW_TYPE_USER = 2
    }

    // ✅ FIXED - Pass viewType to constructor
    inner class MessageViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        // ✅ FIXED - Use when expression based on viewType
        val messageTextView: TextView = when (viewType) {
            VIEW_TYPE_BOT -> itemView.findViewById(R.id.tv_bot_message)
            VIEW_TYPE_USER -> itemView.findViewById(R.id.tv_user_message)
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes = when (viewType) {
            VIEW_TYPE_BOT -> R.layout.item_message_bot
            VIEW_TYPE_USER -> R.layout.item_message_user
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return MessageViewHolder(view, viewType) // ✅ FIXED - Pass viewType!
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        // ✅ IMPROVED - Remove prefix untuk clean display
        val message = messages[position]
            .removePrefix("Anda: ")
            .removePrefix("Bot: ")

        holder.messageTextView.text = message
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].startsWith("Anda:")) {
            VIEW_TYPE_USER
        } else {
            VIEW_TYPE_BOT
        }
    }

    // ✅ BONUS - Helper function untuk add message
    fun addMessage(message: String) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    // ✅ BONUS - Helper function untuk clear messages
    fun clearMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }
}