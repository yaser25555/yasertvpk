package com.yassertv.data

object Config {
  var API_URL = "http://192.142.31.13:8080"
  var STREAMING_URL = "http://t77ert.top:8080"
  var USERNAME = "28438439468494"
  var PASSWORD = "2eUmaHSdOgM2WcJ"
  var GENRAL_API_URL = "https://koralive.lol/aghmdev/kooralive/api/v26"
  var USER_AGENT = "okhttp/5.0.0-alpha.2"
  var HOST_HEADER = "t77ert.top:8080"
  var SUPABASE_URL = "https://lridhqjsdbtcoukecghx.supabase.co"
  var SUPABASE_KEY = "sb_publishable_5nS3CYJMS4kRN9ABXHFKPg_cv1uI8fU"

  fun applyRemote(cfg: RemoteConfigData) {
    if (cfg.apiUrl.isNotBlank()) API_URL = cfg.apiUrl
    if (cfg.streamingUrl.isNotBlank()) STREAMING_URL = cfg.streamingUrl
    if (cfg.username.isNotBlank()) USERNAME = cfg.username
    if (cfg.password.isNotBlank()) PASSWORD = cfg.password
    if (cfg.userAgent.isNotBlank()) USER_AGENT = cfg.userAgent
    if (cfg.hostHeader.isNotBlank()) HOST_HEADER = cfg.hostHeader
    if (cfg.genralApiUrl.isNotBlank()) GENRAL_API_URL = cfg.genralApiUrl
    if (cfg.supabaseUrl.isNotBlank()) SUPABASE_URL = cfg.supabaseUrl
    if (cfg.supabaseKey.isNotBlank()) SUPABASE_KEY = cfg.supabaseKey
  }
}
