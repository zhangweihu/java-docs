using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 演示 4：完整小游戏「接宝石」（第四章实战）。
    /// 按数字键 4 进入本演示。
    ///
    /// 玩法：宝石从天上掉落，左右移动挡板接住它。接住 +10 分，漏接 -1 命，
    /// 生命归零游戏结束，按 R 重新开始。掉落间隔随时间缩短（难度曲线）。
    ///
    /// 设计要点（与第四章一一对应）：
    ///   1. 状态机：Playing / GameOver 用布尔 _gameOver 表达；
    ///   2. Update：移动挡板 + 计时生成（难度递增）；
    ///   3. LateUpdate：遍历子对象让宝石下落并做“接住/漏接”判定；
    ///      用“先移动挡板再判定宝石”的先后顺序避免半帧错位；
    ///   4. 从后往前遍历 + Destroy，规避帧末删除导致的遍历跳过；
    ///   5. 不引入 Rigidbody —— 能简单线性下落就不引物理；
    ///   6. OnGUI 画 HUD 与结束画面。
    /// </summary>
    public class CatchGame : MonoBehaviour
    {
        // ---- Inspector 可调参数 ----
        public float moveSpeed = 9f;            // 挡板移动速度
        public float fallSpeed = 5f;            // 宝石下落速度
        public float startInterval = 1.2f;      // 初始生成间隔(秒)
        public float minInterval = 0.4f;        // 最小生成间隔
        public float speedUpStep = 0.015f;      // 每次生成后间隔减少量
        public int maxLives = 3;

        // ---- 世界边界（顶视视角：X 左右、Y 上下）----
        private const float MinX = -8f, MaxX = 8f;
        private const float TopY = 8f, BottomY = -5f;

        // ---- 内部状态 ----
        private GameObject _paddle;
        private int _lives;
        private int _score;
        private float _spawnTimer;
        private float _spawnInterval;
        private bool _gameOver;

        private void Start()
        {
            // 相机：正交 + 面向 XY 平面，宝石从顶部落下
            Camera cam = Camera.main;
            if (cam != null)
            {
                cam.orthographic = true;
                cam.orthographicSize = 9f;
                cam.transform.position = new Vector3(0f, 0f, -10f);
            }

            // 挡板：一个拉扁的 Cube（不加 Rigidbody，纯逻辑移动更稳）
            _paddle = GameObject.CreatePrimitive(PrimitiveType.Cube);
            _paddle.name = "Paddle";
            _paddle.transform.SetParent(transform);
            _paddle.transform.localScale = new Vector3(3.4f, 0.8f, 1f);
            _paddle.transform.position = new Vector3(0f, BottomY + 1f, 0f);
            _paddle.GetComponent<Renderer>().material.color = Color.cyan;

            Restart();
        }

        private void Update()
        {
            if (_gameOver)
            {
                if (Input.GetKeyDown(KeyCode.R)) Restart();
                return;
            }

            // 1) 移动挡板：读横轴（A/D、←/→）
            float h = Input.GetAxisRaw("Horizontal");
            Vector3 p = _paddle.transform.position;
            p.x += h * moveSpeed * Time.deltaTime;
            p.x = Mathf.Clamp(p.x, MinX, MaxX);
            _paddle.transform.position = p;

            // 2) 计时生成宝石，间隔逐渐变短 -> 难度曲线
            _spawnTimer += Time.deltaTime;
            if (_spawnTimer >= _spawnInterval)
            {
                _spawnTimer = 0f;
                SpawnGem();
                _spawnInterval = Mathf.Max(minInterval, _spawnInterval - speedUpStep);
            }
        }

        private void LateUpdate()
        {
            if (_gameOver) return;

            // 从后往前遍历，安全地在遍历中销毁子对象
            for (int i = transform.childCount - 1; i >= 0; i--)
            {
                Transform gem = transform.GetChild(i);
                if (gem.name != "Gem") continue; // 跳过挡板等非宝石子对象

                // 下落
                Vector3 pos = gem.position;
                pos.y -= fallSpeed * Time.deltaTime;
                gem.position = pos;

                // 接住判定：宝石中心接近挡板顶部接收区
                Vector3 paddlePos = _paddle.transform.position;
                bool caught = pos.y <= paddlePos.y + 0.6f &&
                              Mathf.Abs(pos.x - paddlePos.x) <= 2.0f;
                if (caught)
                {
                    _score += 10;
                    Destroy(gem.gameObject);
                    continue;
                }

                // 漏接：跌出底部
                if (pos.y < BottomY - 0.5f)
                {
                    Destroy(gem.gameObject);
                    _lives--;
                    if (_lives <= 0)
                    {
                        _gameOver = true;
                        Debug.LogWarning("游戏结束！按 R 重新开始");
                    }
                }
            }
        }

        private void SpawnGem()
        {
            var gem = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            gem.name = "Gem";
            gem.transform.SetParent(transform);
            float size = Random.Range(0.6f, 1.2f);
            gem.transform.localScale = Vector3.one * size;
            gem.transform.position = new Vector3(
                Random.Range(MinX + 1f, MaxX - 1f),
                TopY,
                0f);
            gem.GetComponent<Renderer>().material.color = Random.ColorHSV(0f, 1f, 0.7f, 1f, 0.8f, 1f);
        }

        private void Restart()
        {
            // 清空已有宝石（保留挡板）
            for (int i = transform.childCount - 1; i >= 0; i--)
            {
                Transform c = transform.GetChild(i);
                if (c.name == "Gem") Destroy(c.gameObject);
            }

            _score = 0;
            _lives = maxLives;
            _spawnInterval = startInterval;
            _spawnTimer = 0f;
            _gameOver = false;
        }

        private void OnGUI()
        {
            // 顶部 HUD（IMGUI：屏幕像素坐标，左上为原点）
            if (_gameOver)
            {
                string msg = $"游戏结束！ 得分 {_score}\n按 R 重新开始，Esc 返回演示选择";
                GUI.Box(new Rect(Screen.width / 2f - 160, Screen.height / 2f - 60, 320, 120), msg);
                return;
            }

            string hud = $"得分:{_score}    生命:{_lives}    (←/→ 或 A/D 移动挡板接宝石)";
            GUI.Label(new Rect(10, 40, 600, 30), hud);
        }
    }
}
