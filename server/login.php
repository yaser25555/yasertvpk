<?php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');
header('Content-Type: application/json; charset=utf-8');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$username = '';
$password = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = json_decode(file_get_contents('php://input'), true);
    $username = $input['username'] ?? $_POST['username'] ?? '';
    $password = $input['password'] ?? $_POST['password'] ?? '';
} else {
    $username = $_GET['username'] ?? '';
    $password = $_GET['password'] ?? '';
}

if (empty($username) || empty($password)) {
    echo json_encode([
        'mac_registered' => false,
        'message' => 'Username and password are required'
    ]);
    exit;
}

$usersFile = __DIR__ . '/users.json';
if (!file_exists($usersFile)) {
    echo json_encode([
        'mac_registered' => false,
        'message' => 'Server configuration error'
    ]);
    exit;
}

$users = json_decode(file_get_contents($usersFile), true);

if (isset($users[$username]) && $users[$username] === $password) {
    echo json_encode([
        'mac_registered' => true,
        'message' => 'Login successful',
        'expire_date' => '2029-12-31',
        'plan_id' => '1',
        'is_trial' => 0,
        'lock' => 0,
        'mac_address' => 'AA:BB:CC:DD:EE:FF',
        'device_key' => 'yaser_' . $username,
        'pin' => '0000',
        'notification_tital' => 'yaser.tv',
        'notification_content' => 'Welcome to yaser.tv',
        'app_themes' => '',
        'log_themes' => '',
        'login_tital' => 'yaser.tv',
        'login_content' => 'Welcome',
        'licen_key' => 'activated',
        'parent_synced' => 0,
        'parent_control' => '',
        'price' => '0',
        'apk_link' => '',
        'app_version' => '1.0',
        'urls' => [
            [
                'id' => '1',
                'name' => 'Main',
                'url' => 'https://example.com/playlist.m3u',
                'type' => 'm3u',
                'is_protected' => '0'
            ]
        ]
    ]);
} else {
    echo json_encode([
        'mac_registered' => false,
        'message' => 'Invalid username or password'
    ]);
}
