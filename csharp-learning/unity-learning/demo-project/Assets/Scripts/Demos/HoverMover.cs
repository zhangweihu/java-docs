using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 用 Mathf.PingPong 让方块在起始点上方来回漂浮（纯运动学，不涉及物理）。
    /// 配合第二章 2.3 阅读：Time.time 是自游戏开始累计的秒数，
    /// Mathf.PingPong(t, length) 会返回一个在 [0, length] 间来回波动的值。
    /// </summary>
    public class HoverMover : MonoBehaviour
    {
        public float height = 1.5f;
        public float speed = 1.5f;

        private Vector3 _start;

        private void Awake() => _start = transform.position;

        private void Update()
        {
            float y = Mathf.PingPong(Time.time * speed, height);
            transform.position = _start + new Vector3(0f, y, 0f);
        }
    }
}
