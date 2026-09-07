using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 演示 1：MonoBehaviour 生命周期与组件（配合第二章阅读）。
    /// 按数字键 1 进入本演示。会在场景中生成三个方块：
    ///   1) LifecycleCube —— 挂着 LifecycleLogger，在 Console 打印 Awake/OnEnable/Start/Update 顺序；
    ///   2) Spinner      —— 挂着 Spinner，演示“每帧旋转 = 方向 × 速度 × Time.deltaTime”句式；
    ///   3) HoverCube    —— 挂着 HoverMover，用 Mathf.PingPong 演示在 Update 里做运动学移动。
    /// 配套文件：LifecycleLogger.cs / Spinner.cs / HoverMover.cs（各为一个组件类，文件名与类名一致）。
    /// </summary>
    public class LifecycleDemo : MonoBehaviour
    {
        private void Start()
        {
            // 定位相机：从正前方观察（正交视图）
            Camera cam = Camera.main;
            if (cam != null)
            {
                cam.orthographic = true;
                cam.orthographicSize = 5f;
                cam.transform.position = new Vector3(0, 0, -10);
            }

            // 地面
            var floor = GameObject.CreatePrimitive(PrimitiveType.Plane);
            floor.name = "Floor";
            floor.transform.SetParent(transform);
            floor.transform.localScale = Vector3.one * 0.4f; // 缩小到约 4x4
            floor.transform.position = new Vector3(0, -2.5f, 0);

            // 1) 生命周期观察方块
            var cubeA = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cubeA.name = "LifecycleCube";
            cubeA.transform.SetParent(transform);
            cubeA.transform.position = new Vector3(-2f, -1.2f, 0);
            cubeA.GetComponent<Renderer>().material.color = Color.cyan;
            cubeA.AddComponent<LifecycleLogger>();   // 看 Console 输出

            // 2) 自转方块
            var cubeB = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cubeB.name = "Spinner";
            cubeB.transform.SetParent(transform);
            cubeB.transform.position = new Vector3(0f, -1.2f, 0);
            cubeB.GetComponent<Renderer>().material.color = Color.magenta;
            cubeB.AddComponent<Spinner>();

            // 3) 上下漂浮方块（PingPong 演示）
            var cubeC = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cubeC.name = "HoverCube";
            cubeC.transform.SetParent(transform);
            cubeC.transform.position = new Vector3(2f, -1.2f, 0);
            cubeC.GetComponent<Renderer>().material.color = Color.yellow;
            cubeC.AddComponent<HoverMover>();
        }
    }
}
