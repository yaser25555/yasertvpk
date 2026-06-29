<?php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/supabase_config.php';

$action = $_GET['action'] ?? '';

function supabaseApi($method, $path, $body = null) {
  $url = SUPABASE_URL . $path;
  $headers = [
    'Content-Type: application/json',
    'apikey: ' . SUPABASE_SERVICE_KEY,
    'Authorization: Bearer ' . SUPABASE_SERVICE_KEY,
    'Accept: application/json',
    'Prefer: return=minimal'
  ];

  $context = stream_context_create([
    'http' => [
      'method' => $method,
      'header' => implode("\r\n", $headers),
      'timeout' => 15,
      'ignore_errors' => true
    ]
  ]);

  if ($body !== null) {
    $context = stream_context_create([
      'http' => [
        'method' => $method,
        'header' => implode("\r\n", $headers),
        'content' => json_encode($body),
        'timeout' => 15,
        'ignore_errors' => true
      ]
    ]);
  }

  $response = @file_get_contents($url, false, $context);
  return ['body' => $response !== false ? $response : ''];
}

if ($action === 'list') {
  $result = supabaseApi('GET', '/rest/v1/app_users?select=*&order=id.asc');
  $users = json_decode($result['body'], true) ?: [];
  echo json_encode(['success' => true, 'users' => $users]);
  exit;
}

if ($action === 'add') {
  $input = json_decode(file_get_contents('php://input'), true);
  $newUser = $input['username'] ?? '';
  $newPass = $input['password'] ?? '';
  if (empty($newUser) || empty($newPass)) {
    echo json_encode(['success' => false, 'message' => 'Missing username or password']);
    exit;
  }
  $expiresAt = $input['expires_at'] ?? date('Y-m-d', strtotime('+1 year'));
  supabaseApi('POST', '/rest/v1/app_users', [
    'username' => $newUser,
    'password' => $newPass,
    'expires_at' => $expiresAt,
    'active' => true
  ]);
  echo json_encode(['success' => true, 'message' => 'User added']);
  exit;
}

if ($action === 'delete') {
  $id = $_GET['id'] ?? '';
  supabaseApi('DELETE', "/rest/v1/app_users?id=eq.$id");
  echo json_encode(['success' => true, 'message' => 'User deleted']);
  exit;
}

if ($action === 'verify') {
  $id = $_GET['id'] ?? '';
  supabaseApi('PATCH', "/rest/v1/app_users?id=eq.$id", ['active' => true]);
  echo json_encode(['success' => true]);
  exit;
}

if ($action === 'unverify') {
  $id = $_GET['id'] ?? '';
  supabaseApi('PATCH', "/rest/v1/app_users?id=eq.$id", ['active' => false]);
  echo json_encode(['success' => true]);
  exit;
}

if ($action === 'update_password') {
  $input = json_decode(file_get_contents('php://input'), true);
  $id = $input['id'] ?? '';
  $newPass = $input['password'] ?? '';
  if (empty($id) || empty($newPass)) {
    echo json_encode(['success' => false, 'message' => 'Missing id or password']);
    exit;
  }
  supabaseApi('PATCH', "/rest/v1/app_users?id=eq.$id", ['password' => $newPass]);
  echo json_encode(['success' => true, 'message' => 'Password updated']);
  exit;
}

if ($action === 'update_expiry') {
  $input = json_decode(file_get_contents('php://input'), true);
  $id = $input['id'] ?? '';
  $date = $input['expires_at'] ?? '';
  if (empty($id) || empty($date)) {
    echo json_encode(['success' => false, 'message' => 'Missing id or date']);
    exit;
  }
  supabaseApi('PATCH', "/rest/v1/app_users?id=eq.$id", ['expires_at' => $date]);
  echo json_encode(['success' => true, 'message' => 'Expiry updated']);
  exit;
}

if ($action === 'change_username') {
  $input = json_decode(file_get_contents('php://input'), true);
  $id = $input['id'] ?? '';
  $newUsername = $input['username'] ?? '';
  if (empty($id) || empty($newUsername)) {
    echo json_encode(['success' => false, 'message' => 'Missing id or username']);
    exit;
  }
  supabaseApi('PATCH', "/rest/v1/app_users?id=eq.$id", ['username' => $newUsername]);
  echo json_encode(['success' => true, 'message' => 'Username updated']);
  exit;
}

echo json_encode(['success' => false, 'message' => 'Unknown action']);
