using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 每帧自转：方向 × 速度(度/秒) × Time.deltaTime。
    /// 配合第二章 2.3 阅读：移动/旋转类代码必须乘以 deltaTime 才能与帧率无关。
    /// </summary>
    public class Spinner : MonoBehaviour
    {
        public float speed = 90f; // Inspector 可调

        private void Update()
        {
            transform.Rotate(0f, speed * Time.deltaTime, 0f);
        }
    }
}
