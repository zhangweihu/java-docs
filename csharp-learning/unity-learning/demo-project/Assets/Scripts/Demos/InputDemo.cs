using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 演示 2：用旧输入系统（Input）控制物体移动（配合第二章 2.3 与第三章 3.3 阅读）。
    /// 按数字键 2 进入本演示。
    /// 按 ←/→ 或 A/D 移动绿色方块；按 ↑/↓ 或 W/S 前后移动。
    /// 演示了三个重点：
    ///   1) Input.GetAxisRaw 读取输入轴（-1~1）；
    ///   2) 移动要乘 Time.deltaTime，保证速度与帧率无关；
    ///   3) transform.Translate 做平移，再用 Mathf.Clamp 夹在世界边界内。
    /// </summary>
    public class InputDemo : MonoBehaviour
    {
        public float moveSpeed = 5f;
        public float boundX = 4.5f; // 10x10 地面半宽约 5，留一点边距
        public float boundZ = 4.5f;

        private GameObject _player;

        private void Start()
        {
            Camera cam = Camera.main;
            if (cam != null)
            {
                cam.orthographic = true;
                cam.orthographicSize = 7f;
                cam.transform.position = new Vector3(0, 6.5f, -6f); // 斜俯视视角
                cam.transform.LookAt(Vector3.zero);
            }

            // 地面（用于观察移动范围）
            var floor = GameObject.CreatePrimitive(PrimitiveType.Plane);
            floor.name = "Floor";
            floor.transform.SetParent(transform);
            floor.transform.localScale = Vector3.one; // 10x10 地面

            // 玩家方块
            _player = GameObject.CreatePrimitive(PrimitiveType.Cube);
            _player.name = "PlayerCube";
            _player.transform.SetParent(transform);
            _player.transform.position = new Vector3(0f, 0.5f, 0f);
            _player.GetComponent<Renderer>().material.color = Color.green;
        }

        private void Update()
        {
            if (_player == null) return;

            // 水平/前后输入轴：键盘 A/D、←/→ 与 W/S、↑/↓
            float h = Input.GetAxisRaw("Horizontal");
            float v = Input.GetAxisRaw("Vertical");

            // 方向 × 速度 × deltaTime —— 经典移动句式
            Vector3 move = new Vector3(h, 0f, v) * (moveSpeed * Time.deltaTime);
            _player.transform.Translate(move, Space.World);

            // 夹在边界内（XZ 平面）
            Vector3 p = _player.transform.position;
            p.x = Mathf.Clamp(p.x, -boundX, boundX);
            p.z = Mathf.Clamp(p.z, -boundZ, boundZ);
            _player.transform.position = p;
        }

        private void OnGUI()
        {
            GUI.Label(new Rect(10, 34, 400, 24),
                $"方向键/WASD 移动   当前速度={moveSpeed:F1}");
        }
    }
}
