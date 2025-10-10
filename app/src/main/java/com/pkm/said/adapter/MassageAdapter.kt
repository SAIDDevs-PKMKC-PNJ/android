package com.pkm.said.adapter

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.pkm.said.R

class MessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onReloadResponse: (Int) -> Unit ={}
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_BOT = 1
        private const val VIEW_TYPE_USER = 2
        private const val VIEW_TYPE_USER_SPEECH = 3
        private const val VIEW_TYPE_BOT_LOADING = 4
    }

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val isVoice: Boolean = false,
        val isLoading: Boolean = false,
        val messageId: String = System.currentTimeMillis().toString()
    )

    // ✅ FIXED - Pass viewType to constructor
    inner class MessageViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView? = when (viewType) {
            VIEW_TYPE_BOT -> itemView.findViewById(R.id.tv_bot_message)
            VIEW_TYPE_USER -> itemView.findViewById(R.id.tv_user_message)
            VIEW_TYPE_USER_SPEECH -> itemView.findViewById(R.id.tv_user_speech_message)
            VIEW_TYPE_BOT_LOADING -> null
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }

        private val botFooter: ViewGroup? = itemView.findViewById(R.id.bot_message_footer)
        private val btnReload: MaterialButton? = itemView.findViewById(R.id.btn_reload_response)
        private val loadingIndicator: ViewGroup? = itemView.findViewById(R.id.loading_indicator)
        private val tvTypingDots: TextView? = itemView.findViewById(R.id.tv_typing_dots)

        fun bind(message: ChatMessage, position: Int) {
            when (viewType) {
                VIEW_TYPE_BOT -> {
                    messageTextView?.text = message.text.removePrefix("Bot: ")
                    // ✅ Tampilkan reload button untuk bot messages
                    botFooter?.visibility = View.VISIBLE
                    btnReload?.setOnClickListener {
                        onReloadResponse(position)
                    }
                }

                VIEW_TYPE_BOT_LOADING -> {
                    // ✅ Handle loading state
                    loadingIndicator?.visibility = View.VISIBLE
                    startTypingAnimation()
                }

                VIEW_TYPE_USER -> {
                    messageTextView?.text = message.text.removePrefix("Anda: ")
                }

                VIEW_TYPE_USER_SPEECH -> {
                    messageTextView?.text = message.text.removePrefix("Anda (via suara):")
                }
            }
        }

        private fun startTypingAnimation() {
            // Animasi typing dots
            val animator = ValueAnimator.ofInt(1, 4).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val dots = ".".repeat(animator.animatedValue as Int)
                    tvTypingDots?.text = dots
                }
            }
            animator.start()
        }
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes = when (viewType) {
            VIEW_TYPE_BOT -> R.layout.item_message_bot
            VIEW_TYPE_USER -> R.layout.item_message_user
            VIEW_TYPE_USER_SPEECH -> R.layout.item_message_user_speech
            VIEW_TYPE_BOT_LOADING -> R.layout.item_message_bot_loading
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        // ✅ IMPROVED - Remove prefix untuk clean display
        holder.bind(messages[position], position)
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isLoading -> VIEW_TYPE_BOT_LOADING
            message.isVoice -> VIEW_TYPE_USER_SPEECH
            message.isUser -> VIEW_TYPE_USER
            else -> VIEW_TYPE_BOT
        }
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun addLoadingMessage() {
        val loadingMessage = ChatMessage("", false, false, true)
        messages.add(loadingMessage)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLoadingToMessage(botMessage: ChatMessage) {
        if (messages.isNotEmpty() && messages.last().isLoading) {
            messages.removeAt(messages.size - 1)
            messages.add(botMessage)
            notifyDataSetChanged()
        }
    }

    fun clearMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }
}