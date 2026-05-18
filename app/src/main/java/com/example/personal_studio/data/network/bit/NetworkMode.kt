package com.example.personal_studio.data.network.bit

/** Whether to route BIT requests directly (campus network) or via WebVPN (off-campus). */
enum class NetworkMode {
    LOCAL,    // login.bit.edu.cn — only reachable on校园网
    WEBVPN,   // webvpn.bit.edu.cn — reachable anywhere after WebVPN account login
}
