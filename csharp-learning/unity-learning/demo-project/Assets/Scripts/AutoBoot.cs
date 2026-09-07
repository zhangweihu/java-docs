using UnityEngine;

namespace UnityLearning
{
    /// <summary>
    /// 演示工程自举入口（配合第二章 2.1 与第四章 4.4 阅读）。
    ///
    /// 仓库里的 demo 工程没有 .unity 场景文件——世界完全由代码在运行时搭建。
    /// 本类利用 [RuntimeInitializeOnLoadMethod] 特性，在场景加载完成后自动运行，
    /// 若场景里还没有 DemoManager 就创建一个并挂上组件。
    ///
    /// 真实项目里通常不需要这种自举：直接在编辑器里搭场景、把脚本挂到对象上即可。
    /// </summary>
    public static class AutoBoot
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Boot()
        {
            if (Object.FindFirstObjectByType<DemoManager>() == null)
            {
                var go = new GameObject("DemoManager");
                go.AddComponent<DemoManager>();
            }
        }
    }
}
