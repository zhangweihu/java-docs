using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 打印 MonoBehaviour 生命周期的关键时机，挂在任意对象上即可观察。
    /// 配合第二章 2.2 阅读：Awake → OnEnable → Start → Update … → OnDisable → OnDestroy。
    /// </summary>
    public class LifecycleLogger : MonoBehaviour
    {
        private int _frames;

        private void Awake()
        {
            Debug.Log($"[{name}] Awake() —— 对象被创建时调用（即使脚本未启用）");
        }

        private void OnEnable()
        {
            Debug.Log($"[{name}] OnEnable() —— 对象/脚本变为启用时调用");
        }

        private void Start()
        {
            Debug.Log($"[{name}] Start() —— 第一次 Update 前调用（只一次）");
        }

        private void Update()
        {
            _frames++;
            if (_frames == 3)
            {
                Debug.Log($"[{name}] Update() —— 每帧调用，已到第 3 帧");
            }
        }

        private void OnDisable()
        {
            Debug.Log($"[{name}] OnDisable() —— 对象被禁用时调用");
        }

        private void OnDestroy()
        {
            Debug.Log($"[{name}] OnDestroy() —— 对象被销毁前调用");
        }
    }
}
