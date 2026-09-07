using UnityEngine;

namespace UnityLearning
{
    /// <summary>
    /// 演示总控（配合第一章 1.4、第二章 2.1、第四章 4.4 阅读）。
    ///
    /// 职责：
    ///  1. 保证场景里有相机与方向光（场景为空也能跑）；
    ///  2. 用代码创建演示对象并 AddComponent 对应脚本；
    ///  3. 监听键盘：1~5 切换五个演示（1~4 对应主线四章，5 为 2D 精灵演示），Esc 清空当前演示。
    ///
    /// 组件式开发示例：本对象本身没有任何渲染/物理能力，
    /// 它只是“调度器”，通过 AddComponent 把行为组件装到新建对象上。
    /// </summary>
    public class DemoManager : MonoBehaviour
    {
        private GameObject _current;

        private void Start()
        {
            EnsureCamera();
            EnsureLight();
            StartDemo(4); // 默认进入第四章「接宝石」小游戏
        }

        private void Update()
        {
            // 数字键 1~5 切换各章演示（旧输入系统，需启用 Input Manager）
            if (Input.GetKeyDown(KeyCode.Alpha1)) StartDemo(1);
            else if (Input.GetKeyDown(KeyCode.Alpha2)) StartDemo(2);
            else if (Input.GetKeyDown(KeyCode.Alpha3)) StartDemo(3);
            else if (Input.GetKeyDown(KeyCode.Alpha4)) StartDemo(4);
            else if (Input.GetKeyDown(KeyCode.Alpha5)) StartDemo(5);
            else if (Input.GetKeyDown(KeyCode.Escape)) Clear();
        }

        /// <summary>销毁旧的演示，创建新演示对象并挂上对应组件。</summary>
        public void StartDemo(int id)
        {
            Clear();

            var root = new GameObject("Demo_" + id);
            switch (id)
            {
                case 1: root.AddComponent<Demos.LifecycleDemo>();  break;  // 第二章 生命周期/组件
                case 2: root.AddComponent<Demos.InputDemo>();     break;  // 第二章 输入移动
                case 3: root.AddComponent<Demos.PhysicsDemo>();   break;  // 第三章 物理投掷
                case 4: root.AddComponent<Demos.CatchGame>();     break;  // 第四章 完整小游戏
                default: root.AddComponent<Demos.Sprite2DDemo>(); break;  // 第七章 2D 精灵与 2D 物理
            }
            _current = root;
        }

        private void Clear()
        {
            if (_current != null) Destroy(_current);
        }

        /// <summary>若场景没有主相机则创建一个（演示用）。</summary>
        public static void EnsureCamera()
        {
            if (Camera.main != null) return;
            var go = new GameObject("Main Camera");
            go.tag = "MainCamera";
            go.AddComponent<Camera>();
        }

        /// <summary>若场景没有光源则创建一个方向光，避免材质发黑。</summary>
        public static void EnsureLight()
        {
            if (Object.FindFirstObjectByType<Light>() != null) return;
            var go = new GameObject("Directional Light");
            go.AddComponent<Light>();
            go.transform.rotation = Quaternion.Euler(50f, -30f, 0f);
        }

        private void OnGUI()
        {
            GUI.Box(new Rect(4, 4, Screen.width - 8, 24),
                "数字键 1=生命周期  2=输入移动  3=物理投掷  4=接宝石(完整游戏)  5=2D精灵(第七章)  Esc=清空");
        }
    }
}
