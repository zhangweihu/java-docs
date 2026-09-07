using UnityEngine;

namespace UnityLearning.Demos
{
    /// <summary>
    /// 演示 5：2D 精灵与 2D 物理（配合第七章阅读）。按数字键 5 进入。
    ///
    /// 演示重点：
    ///   1) 零美术资源自举：用 Texture2D 画像素 → Sprite.Create 生成精灵（第七章 7.5）；
    ///   2) SpriteRenderer：贴图是纯白，颜色由 SpriteRenderer.color 着色；
    ///   3) 2D 物理：静态 Rigidbody2D 的地面 + 动态 Rigidbody2D 的彩球，
    ///      BoxCollider2D / CircleCollider2D 负责碰撞，重力由引擎演算（第七章 7.4）；
    ///   4) 正交相机：2D 世界沿 XY 平面观察（与 3D 透视相机对比）。
    ///
    /// 玩法：鼠标左键点击生成一个彩色小圆球，它会受重力落下并与地面/其他球碰撞、堆积。
    /// </summary>
    public class Sprite2DDemo : MonoBehaviour
    {
        // ---- 可调参数 ----
        public float groundWidth = 30f;   // 地面宽度
        public float groundY = -6f;       // 地面高度（世界 Y）
        public int maxGems = 30;          // 同屏小球上限，超出销毁最早的

        private Sprite _square;           // 白色方块贴图（地面用，纯色 4x4 就够）
        private Sprite _circle;           // 白色圆形贴图（彩球用，64x64 抗锯齿更好）
        private Transform _gems;          // 所有彩球的父对象：统一挂在演示根下便于清理/计数

        /// <summary>生成一张“纯色可着色”的精灵。核心：像素 → 纹理 → Sprite.Create。</summary>
        private static Sprite MakeSolidSprite(int sizePx, bool circle)
        {
            var tex = new Texture2D(sizePx, sizePx, TextureFormat.RGBA32, false);
            var pixels = new Color[sizePx * sizePx];

            float center = (sizePx - 1) * 0.5f;
            float radius = center;                       // 让圆内切整个纹理
            for (int y = 0; y < sizePx; y++)
            {
                for (int x = 0; x < sizePx; x++)
                {
                    bool inside = !circle || Vector2.Distance(
                        new Vector2(x, y), new Vector2(center, center)) <= radius;
                    pixels[y * sizePx + x] = inside ? Color.white : Color.clear;
                }
            }

            tex.SetPixels(pixels);
            tex.Apply();

            // pixelsPerUnit = sizePx ⇒ 整张图正好是 1×1 世界单位
            return Sprite.Create(tex, new Rect(0, 0, sizePx, sizePx), new Vector2(0.5f, 0.5f), sizePx);
        }

        private void Start()
        {
            // 相机设为“看 XY 平面”的正交视角（第七章 7.1）
            Camera cam = Camera.main;
            if (cam != null)
            {
                cam.orthographic = true;
                cam.orthographicSize = 7f;
                cam.transform.position = new Vector3(0f, 0f, -10f);
            }

            _square = MakeSolidSprite(4, circle: false);
            _circle = MakeSolidSprite(64, circle: true);

            // 彩球统一放到一个空父对象下：挂在本演示根 → Esc 清空时能一并销毁
            _gems = new GameObject("Gems").transform;
            _gems.SetParent(transform);

            CreateGround();
        }

        private void Update()
        {
            if (Input.GetMouseButtonDown(0))
                SpawnGemAtMouse();
        }

        /// <summary>造一块参与 2D 物理的静态地面（贴图是纯白色方块）。</summary>
        private void CreateGround()
        {
            var ground = NewSpriteObject("Ground", _square, new Color(0.7f, 0.7f, 0.7f));
            ground.transform.SetParent(transform);
            ground.transform.localScale = new Vector3(groundWidth, 1f, 1f);
            ground.transform.position = new Vector3(0f, groundY, 0f);

            var rb = ground.AddComponent<Rigidbody2D>();
            rb.bodyType = RigidbodyType2D.Static;   // 地面不动
            ground.AddComponent<BoxCollider2D>();   // 碰撞区域 = 精灵大小 × 缩放
        }

        /// <summary>在鼠标点处生成一颗 2D 彩球，交给物理引擎下落。</summary>
        private void SpawnGemAtMouse()
        {
            Camera cam = Camera.main;
            if (cam == null) return;

            // 屏幕坐标 → 世界坐标：正交相机下给一个到世界的深度距离
            Vector3 mouse = Input.mousePosition;
            mouse.z = Mathf.Abs(cam.transform.position.z);   // 相机在 z=-10，平面在 z=0
            Vector3 world = cam.ScreenToWorldPoint(mouse);
            world.z = 0f;

            // 别点到地面以下
            if (world.y <= groundY + 0.8f) world.y = groundY + 0.8f;

            var gem = NewSpriteObject("Gem", _circle, Random.ColorHSV(0f, 1f, 0.7f, 1f, 0.8f, 1f));
            gem.transform.SetParent(_gems);         // 统一挂在 Gems 父对象下
            gem.transform.position = world;
            gem.AddComponent<Rigidbody2D>();        // 默认 Dynamic + GravityScale=1
            gem.AddComponent<CircleCollider2D>();   // 半径 0.5 世界单位，与 1x1 圆精灵匹配

            // 同屏数量上限：销毁最早生成的那颗（Gems 下第一个子对象）
            if (_gems.childCount > maxGems)
                Destroy(_gems.GetChild(0).gameObject);
        }

        /// <summary>组装一个带 SpriteRenderer 的精灵对象（公共样版方法）。</summary>
        private static GameObject NewSpriteObject(string name, Sprite sprite, Color color)
        {
            var go = new GameObject(name);
            var sr = go.AddComponent<SpriteRenderer>();
            sr.sprite = sprite;
            sr.color = color;             // 白贴图 × 颜色 = 想要的任何纯色
            return go;
        }

        private void OnGUI()
        {
            GUI.Label(new Rect(10, 34, 600, 24),
                "鼠标左键：在画面里点一下生成 2D 小球 —— 重力/碰撞由 Rigidbody2D + Collider2D 演算");
        }
    }
}
