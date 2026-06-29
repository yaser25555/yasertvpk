<?php
session_start();

$usersFile = __DIR__ . '/users.json';
require_once __DIR__ . '/supabase_config.php';

function sbApi($method, $path, $body = null) {
  $url = SUPABASE_URL . $path;
  $headers = [
    'Content-Type: application/json',
    'apikey: ' . SUPABASE_SERVICE_KEY,
    'Authorization: Bearer ' . SUPABASE_SERVICE_KEY,
    'Prefer: return=minimal'
  ];
  $ctx = stream_context_create(['http' => [
    'method' => $method,
    'header' => implode("\r\n", $headers),
    'timeout' => 10,
    'ignore_errors' => true
  ] + ($body !== null ? ['content' => json_encode($body)] : [])]);
  return @file_get_contents($url, false, $ctx);
}

define('ONLINE_THRESHOLD_SEC', 300);

function isUserOnline($lastSeen) {
  if (empty($lastSeen)) return false;
  $ts = strtotime($lastSeen);
  if ($ts === false) return false;
  return (time() - $ts) <= ONLINE_THRESHOLD_SEC;
}

function formatLastSeen($lastSeen) {
  if (empty($lastSeen)) return '—';
  $ts = strtotime($lastSeen);
  if ($ts === false) return '—';
  return date('Y-m-d H:i', $ts);
}

function deviceTypeLabel($type) {
  if ($type === 'tv') return 'تلفزيون';
  if ($type === 'phone') return 'هاتف';
  return '—';
}

$msg = '';
$msgType = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action'])) {
  if ($_POST['action'] === 'login') {
    $pwd = $_POST['password'] ?? '';
    $users = json_decode(file_get_contents($usersFile), true);
    if (isset($users['admin']) && $users['admin'] === $pwd) {
      $_SESSION['admin_logged_in'] = true;
    } else {
      $msg = 'كلمة المرور غير صحيحة';
      $msgType = 'error';
    }
  }

  if ($_POST['action'] === 'logout') {
    session_destroy();
    header('Location: admin.php');
    exit;
  }
}

if (!isset($_SESSION['admin_logged_in']) || !$_SESSION['admin_logged_in']) {
  ?><!DOCTYPE html><html dir="rtl" lang="ar"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تسجيل الدخول</title><style>
  *{margin:0;padding:0;box-sizing:border-box}
  body{font-family:'Segoe UI',Tahoma,sans-serif;background:linear-gradient(135deg,#0f0c29,#302b63,#24243e);min-height:100vh;display:flex;align-items:center;justify-content:center}
  .login-card{background:rgba(255,255,255,0.05);backdrop-filter:blur(10px);padding:40px;border-radius:16px;border:1px solid rgba(255,255,255,0.1);width:100%;max-width:380px;text-align:center}
  .login-card h2{color:#fff;margin-bottom:8px;font-size:24px}
  .login-card p{color:#888;margin-bottom:24px;font-size:14px}
  .login-card input{width:100%;padding:14px 16px;border:1px solid rgba(255,255,255,0.1);border-radius:10px;background:rgba(255,255,255,0.05);color:#fff;font-size:15px;margin-bottom:12px;outline:none;transition:0.2s}
  .login-card input:focus{border-color:#e94560}
  .login-card button{width:100%;padding:14px;border:none;border-radius:10px;background:#e94560;color:#fff;font-size:16px;cursor:pointer;transition:0.2s}
  .login-card button:hover{background:#d63851}
  .login-card .error{background:rgba(233,69,96,0.15);color:#e94560;padding:10px;border-radius:8px;font-size:13px;margin-bottom:12px}
  </style></head><body>
  <div class="login-card">
    <h2>لوحة التحكم</h2>
    <p>يرجى إدخال كلمة المرور</p>
    <?php if ($msg): ?><div class="error"><?=$msg?></div><?php endif; ?>
    <form method="post">
      <input type="hidden" name="action" value="login">
      <input type="password" name="password" placeholder="كلمة المرور" required autofocus>
      <button type="submit">دخول</button>
    </form>
  </div>
  </body></html><?php exit;
}

// Handle POST actions
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
  $a = $_POST['action'] ?? '';

  if ($a === 'add') {
    $u = $_POST['username'] ?? ''; $p = $_POST['password'] ?? ''; $e = $_POST['expires_at'] ?? date('Y-m-d', strtotime('+1 year'));
    if ($u && $p) {
      sbApi('POST', '/rest/v1/app_users', ['username'=>$u,'password'=>$p,'expires_at'=>$e,'active'=>true]);
      $msg = "تم إضافة المستخدم $u بنجاح";
      $msgType = 'success';
    }
  }

  if ($a === 'delete') {
    $del = $_POST['username'] ?? '';
    $list = json_decode(sbApi('GET', '/rest/v1/app_users?select=id,username'), true) ?: [];
    foreach ($list as $u) { if ($u['username'] === $del) { sbApi('DELETE', "/rest/v1/app_users?id=eq.".$u['id']); $msg = "تم حذف المستخدم $del"; $msgType = 'success'; break; } }
  }

  if ($a === 'toggle_active') {
    $id = $_POST['id'] ?? ''; $val = $_POST['val'] ?? 'true';
    sbApi('PATCH', "/rest/v1/app_users?id=eq.$id", ['active' => $val === 'true']);
    $msg = 'تم تغيير الحالة'; $msgType = 'success';
  }

  if ($a === 'update_expiry') {
    $id = $_POST['id'] ?? ''; $date = $_POST['expires_at'] ?? '';
    if ($id && $date) { sbApi('PATCH', "/rest/v1/app_users?id=eq.$id", ['expires_at'=>$date]); $msg = 'تم تحديث تاريخ الصلاحية'; $msgType = 'success'; }
  }

  if ($a === 'change_password') {
    $t = $_POST['username'] ?? ''; $np = $_POST['password'] ?? '';
    if ($t && $np) {
      $list = json_decode(sbApi('GET', '/rest/v1/app_users?select=id,username'), true) ?: [];
      foreach ($list as $u) { if ($u['username'] === $t) { sbApi('PATCH', "/rest/v1/app_users?id=eq.".$u['id'], ['password'=>$np]); $msg = "تم تغيير كلمة مرور $t"; $msgType = 'success'; break; } }
    }
  }
}

$sbUsers = json_decode(sbApi('GET', '/rest/v1/app_users?select=*&order=id.asc'), true) ?: [];
$jsonUsers = json_decode(file_get_contents($usersFile), true) ?: [];
$today = date('Y-m-d');
$onlineCount = 0;
foreach ($sbUsers as $u) {
  if (isUserOnline($u['last_seen'] ?? null)) $onlineCount++;
}
?><!DOCTYPE html><html dir="rtl" lang="ar"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>لوحة التحكم</title><style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Segoe UI',Tahoma,sans-serif;background:#1a1a2e;color:#fff;padding:20px;min-height:100vh}
h1{font-size:24px;margin-bottom:4px}
.subtitle{color:#888;font-size:13px;margin-bottom:24px}
.card{background:#16213e;border-radius:12px;padding:24px;margin-bottom:20px;border:1px solid rgba(255,255,255,0.05)}
.card h3{font-size:16px;margin-bottom:16px;color:#e94560}
table{width:100%;border-collapse:collapse}
th{text-align:right;padding:10px 12px;font-size:12px;color:#888;border-bottom:1px solid rgba(255,255,255,0.05)}
td{padding:10px 12px;font-size:13px;border-bottom:1px solid rgba(255,255,255,0.03)}
tr:last-child td{border-bottom:none}
input,select{padding:10px 14px;border:1px solid rgba(255,255,255,0.1);border-radius:8px;background:rgba(255,255,255,0.05);color:#fff;font-size:14px;outline:none;transition:0.2s;width:100%}
input:focus{border-color:#e94560}
input[type="date"]{color-scheme:dark}
.btn{padding:10px 18px;border:none;border-radius:8px;font-size:13px;cursor:pointer;transition:0.2s;display:inline-flex;align-items:center;gap:4px}
.btn-primary{background:#e94560;color:#fff}.btn-primary:hover{background:#d63851}
.btn-small{padding:6px 12px;font-size:12px}
.btn-success{background:#00c853;color:#fff}.btn-success:hover{background:#00b84a}
.btn-danger{background:#ff1744;color:#fff}.btn-danger:hover{background:#e0143a}
.btn-warning{background:#ff9100;color:#fff}.btn-warning:hover{background:#e08000}
.grid-2{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.msg{padding:12px 16px;border-radius:8px;font-size:13px;margin-bottom:16px;display:flex;align-items:center;gap:8px}
.msg-success{background:rgba(0,200,83,0.1);color:#00c853;border:1px solid rgba(0,200,83,0.2)}
.msg-error{background:rgba(255,23,68,0.1);color:#ff1744;border:1px solid rgba(255,23,68,0.2)}
.badge{display:inline-block;padding:4px 10px;border-radius:20px;font-size:11px;font-weight:600}
.badge-active{background:rgba(0,200,83,0.15);color:#00c853}
.badge-inactive{background:rgba(255,23,68,0.15);color:#ff1746}
.badge-expired{background:rgba(255,145,0,0.15);color:#ff9100}
.badge-online{background:rgba(0,229,255,0.15);color:#00e5ff}
.badge-offline{background:rgba(107,114,128,0.15);color:#9ca3af}
.device-tag{font-size:11px;color:#888;margin-top:2px}
.online-summary{color:#00e5ff;font-size:13px;margin-top:6px}
.actions{display:flex;gap:4px;flex-wrap:wrap}
form.inline{display:inline}
.header-bar{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}
.header-bar h1{margin:0}
.logout-btn{background:transparent;border:1px solid rgba(255,255,255,0.1);color:#888;padding:8px 16px;border-radius:8px;cursor:pointer;font-size:13px;transition:0.2s}
.logout-btn:hover{border-color:#e94560;color:#e94560}
@media(max-width:768px){.grid-2{grid-template-columns:1fr}}
</style></head><body>

<div class="header-bar">
  <div>
    <h1>لوحة التحكم</h1>
    <div class="subtitle">إدارة حسابات YasserTV</div>
    <div class="online-summary">🟢 متصل الآن: <?=$onlineCount?> مستخدم</div>
  </div>
  <form method="post"><button type="submit" name="action" value="logout" class="logout-btn">تسجيل خروج</button></form>
</div>

<?php if ($msg): ?>
<div class="msg msg-<?=$msgType?>">
  <span><?=$msgType === 'success' ? '✓' : '✕'?></span>
  <span><?=$msg?></span>
</div>
<?php endif; ?>

<div class="card">
  <h3>المستخدمين</h3>
  <div style="overflow-x:auto">
  <table>
    <tr><th>#</th><th>المستخدم</th><th>كلمة المرور</th><th>تاريخ الصلاحية</th><th>حالة الحساب</th><th>الاتصال</th><th>آخر نشاط</th><th>الجهاز</th><th>إجراءات</th></tr>
    <?php foreach ($sbUsers as $i=>$u): 
      $isExpired = ($u['expires_at'] ?? '') < $today;
      $status = $isExpired ? 'expired' : (($u['active']??false) ? 'active' : 'inactive');
      $statusText = $isExpired ? 'منتهي' : (($u['active']??false) ? 'نشط' : 'موقف');
      $statusClass = $isExpired ? 'badge-expired' : (($u['active']??false) ? 'badge-active' : 'badge-inactive');
      $isOnline = isUserOnline($u['last_seen'] ?? null);
      $onlineClass = $isOnline ? 'badge-online' : 'badge-offline';
      $onlineText = $isOnline ? 'متصل الآن' : 'غير متصل';
    ?>
    <tr>
      <td style="color:#888"><?=$u['id']?></td>
      <td><strong><?=htmlspecialchars($u['username'])?></strong></td>
      <td style="font-family:monospace;color:#888"><?=htmlspecialchars($u['password']??'')?></td>
      <td><?=htmlspecialchars($u['expires_at']??'')?></td>
      <td><span class="badge <?=$statusClass?>"><?=$statusText?></span></td>
      <td><span class="badge <?=$onlineClass?>"><?=$onlineText?></span></td>
      <td style="color:#888;font-size:12px"><?=htmlspecialchars(formatLastSeen($u['last_seen'] ?? null))?></td>
      <td><?=htmlspecialchars(deviceTypeLabel($u['device_type'] ?? ''))?></td>
      <td class="actions">
        <form method="post" class="inline">
          <input type="hidden" name="action" value="toggle_active">
          <input type="hidden" name="id" value="<?=$u['id']?>">
          <input type="hidden" name="val" value="<?=($u['active']??false)?'false':'true'?>">
          <button type="submit" class="btn btn-small <?=($u['active']??false)?'btn-warning':'btn-success'?>">
            <?=($u['active']??false)?'إيقاف':'تفعيل'?>
          </button>
        </form>
        <form method="post" class="inline" onsubmit="return confirm('حذف <?=htmlspecialchars($u['username'])?>؟')">
          <input type="hidden" name="action" value="delete">
          <input type="hidden" name="username" value="<?=htmlspecialchars($u['username'])?>">
          <button type="submit" class="btn btn-small btn-danger">حذف</button>
        </form>
      </td>
    </tr>
    <?php endforeach; ?>
  </table>
  </div>
</div>

<div class="grid-2">
  <div class="card">
    <h3>إضافة مستخدم جديد</h3>
    <form method="post">
      <input type="hidden" name="action" value="add">
      <div style="display:flex;flex-direction:column;gap:10px">
        <input type="text" name="username" placeholder="اسم المستخدم" required>
        <input type="text" name="password" placeholder="كلمة المرور" required>
        <input type="date" name="expires_at" value="<?=date('Y-m-d', strtotime('+1 year'))?>" required>
        <button type="submit" class="btn btn-primary" style="width:100%;justify-content:center">إضافة المستخدم</button>
      </div>
    </form>
  </div>

  <div class="card">
    <h3>تعديل مستخدم</h3>
    <form method="post">
      <input type="hidden" name="action" value="change_password">
      <div style="display:flex;flex-direction:column;gap:10px">
        <input type="text" name="username" placeholder="اسم المستخدم" required>
        <input type="text" name="password" placeholder="كلمة المرور الجديدة" required>
        <button type="submit" class="btn btn-primary" style="width:100%;justify-content:center">تغيير كلمة المرور</button>
      </div>
    </form>
  </div>
</div>

<div class="card">
  <h3>تحديد صلاحية</h3>
  <form method="post">
    <input type="hidden" name="action" value="update_expiry">
    <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:end">
      <div style="flex:1;min-width:120px">
        <select name="id" required style="margin-bottom:0">
          <option value="">اختر مستخدم</option>
          <?php foreach ($sbUsers as $u): ?>
          <option value="<?=$u['id']?>"><?=htmlspecialchars($u['username'])?></option>
          <?php endforeach; ?>
        </select>
      </div>
      <div style="flex:1;min-width:120px">
        <input type="date" name="expires_at" required>
      </div>
      <button type="submit" class="btn btn-primary">تحديث</button>
    </div>
  </form>
</div>

</body></html>
