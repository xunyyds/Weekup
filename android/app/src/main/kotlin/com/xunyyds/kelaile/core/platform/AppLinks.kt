package com.xunyyds.kelaile.core.platform

object AppLinks {
    const val REPOSITORY = "https://github.com/xunyyds/kelaile"
    const val ANDROID_LIQUID_GLASS = "https://github.com/Kyant0/AndroidLiquidGlass"

    /**
     * 仓库许可文件，指向 main 分支的实际路径。
     * 上游补上 `LICENSE`（或改名 `LICENSE.md`、`COPYING`）后，GitHub 会把该地址重定向到真实文件。
     */
    const val LICENSE = "$REPOSITORY/blob/main/LICENSE"
}
