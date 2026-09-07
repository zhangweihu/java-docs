using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 演示 3：物理系统三件套（配合第三章 3.1~3.2 阅读）。
    /// 按数字键 3 进入本演示。
    /// 鼠标左键点击地面，生成一个带 Rigidbody + SphereCollider 的彩色小球，
    /// 小球受重力下落、与地面碰撞滚动——物理演算全交给引擎。
    /// 同时演示:
    ///   1) GameObject.CreatePrimitive —— 代码创建自带 Collider+Renderer 的几何体；
    ///   2) AddComponent&lt;Rigidbody&gt;() —— 运行时给对象加“物理能力”；
    ///   3) Camera.main.ScreenPointToRay + Physics.Raycast —— 鼠标点选世界坐标。
    /// </summary>
    public class PhysicsDemo : MonoBehaviour
    {
        public float spawnHeight = 6f;     // 小球生成高度
        public float maxBalls = 12;        // 限制小球数量防止卡顿

        private int _ballCount;
        private GameObject _ground;

        private void Start()
        {
            Camera cam = Camera.main;
            if (cam != null)
            {
                cam.orthographic = false;                 // 透视相机
                cam.transform.position = new Vector3(0, 3.5f, -6f);
                cam.transform.LookAt(new Vector3(0, 0, 3f));
            }

            // 地面（Plane 自带 MeshCollider，供小球碰撞与射线检测）
            _ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
            _ground.name = "Ground";
            _ground.transform.SetParent(transform);
            _ground.transform.localScale = Vector3.one * 2f; // 约 20x20
            _ground.GetComponent<Renderer>().material.color = new Color(0.4f, 0.5f, 0.6f);
        }

        private void Update()
        {
            // 鼠标左键（0 = 左键）
            if (Input.GetMouseButtonDown(0))
            {
                SpawnBallAtMouse();
            }
        }

        private void SpawnBallAtMouse()
        {
            if (_ballCount >= maxBalls) return;

            Ray ray = Camera.main != null ? Camera.main.ScreenPointToRay(Input.mousePosition) : default;
            if (!Physics.Raycast(ray, out RaycastHit hit, 1000f)) return;

            var ball = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            ball.name = "Ball_" + _ballCount;
            ball.transform.SetParent(transform);
            // 从鼠标指向的地面点上方一点生成，随机水平偏移让落点更自然
            Vector3 pos = hit.point + Vector3.up * spawnHeight;
            pos.x += Random.Range(-1f, 1f);
            pos.z += Random.Range(-1f, 1f);
            ball.transform.position = pos;
            ball.transform.localScale = Vector3.one * Random.Range(0.6f, 1.3f);

            // 随机颜色（把材质实例化，避免污染默认材质）
            ball.GetComponent<Renderer>().material.color = Random.ColorHSV(0f, 1f, 0.6f, 1f, 0.8f, 1f);

            // 关键：加刚体 -> 物理引擎接管重力与碰撞
            Rigidbody rb = ball.AddComponent<Rigidbody>();
            rb.mass = Random.Range(0.5f, 2f);

            _ballCount++;
            Debug.Log($"生成第 {_ballCount} 个小球 @ {pos}");
        }

        private void OnGUI()
        {
            GUI.Label(new Rect(10, 34, 500, 24),
                $"鼠标左键在地面处生成小球（当前场景 {_ballCount}/{maxBalls}）—— 重力下落与地面碰撞由引擎演算");
        }
    }
}
