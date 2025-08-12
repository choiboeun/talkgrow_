package com.talkgrow_.model

data class NlpRequest(
    val text: String,
    val targetWords: List<String>
)
