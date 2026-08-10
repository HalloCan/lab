# Idea Forge

一个用于测试各种想法和点子的 Android 实验项目。

## 项目结构

```
app/src/main/java/com/htoc/lab/
├── MainActivity.kt       # 主 Activity，承载 NavHostFragment
├── HomeFragment.kt       # 主页，卡片网格展示所有功能入口
├── AboutFragment.kt      # 说明页
└── DemoFragment.kt       # 功能示范模板页
```

## 技术栈

- Kotlin
- Material Design 3 (Material You)
- Navigation Component
- XML Layouts
- minSdk 29 / targetSdk 36

---

## 如何新增一个页面

以新增一个「设置页」为例，共 5 步：

### 1. 创建 Fragment

新建 `app/src/main/java/com/htoc/lab/SettingsFragment.kt`：

```kotlin
package com.htoc.lab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 在这里写页面逻辑
    }
}
```

### 2. 创建布局文件

新建 `app/src/main/res/layout/fragment_settings.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    tools:context=".SettingsFragment">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="设置页面内容"
        android:textAppearance="?attr/textAppearanceBodyLarge" />

</LinearLayout>
```

### 3. 在导航图中注册

编辑 `app/src/main/res/navigation/nav_graph.xml`，在 `<navigation>` 标签内添加：

```xml
<fragment
    android:id="@+id/settingsFragment"
    android:name="com.htoc.lab.SettingsFragment"
    android:label="设置"
    tools:layout="@layout/fragment_settings" />
```

然后在 `homeFragment` 中添加导航 action：

```xml
<action
    android:id="@+id/action_home_to_settings"
    app:destination="@id/settingsFragment"
    app:enterAnim="@anim/nav_default_enter_anim"
    app:exitAnim="@anim/nav_default_exit_anim"
    app:popEnterAnim="@anim/nav_default_pop_enter_anim"
    app:popExitAnim="@anim/nav_default_pop_exit_anim" />
```

### 4. 在主页添加入口卡片

编辑 `HomeFragment.kt`，在 `getFeatureItems()` 列表中添加：

```kotlin
FeatureItem(
    "settings",
    R.drawable.ic_settings,   // 需要一个图标资源
    getString(R.string.card_settings_title),
    getString(R.string.card_settings_desc)
)
```

然后在 `onViewCreated` 的 `when` 中添加导航：

```kotlin
"settings" -> findNavController().navigate(R.id.action_home_to_settings)
```

### 5. 添加字符串资源

编辑 `app/src/main/res/values/strings.xml`，添加：

```xml
<string name="card_settings_title">设置</string>
<string name="card_settings_desc">应用设置和配置选项</string>
```

---

## 文件对应关系速查

| 要改什么 | 改哪个文件 |
|----------|-----------|
| 新增页面逻辑 | 新建 `XxxFragment.kt` |
| 新增页面布局 | 新建 `res/layout/fragment_xxx.xml` |
| 注册导航 | `res/navigation/nav_graph.xml` |
| 主页卡片入口 | `HomeFragment.kt` 的 `getFeatureItems()` |
| 字符串文案 | `res/values/strings.xml` |
| 图标 | `res/drawable/ic_xxx.xml` (矢量图) |
| 主题颜色 | `res/values/colors.xml` + `themes.xml` |
