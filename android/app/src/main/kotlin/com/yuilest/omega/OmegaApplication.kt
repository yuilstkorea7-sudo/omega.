package com.yuilest.omega

import android.app.Application

/**
 * 오메가 프로그램 (Yuil EST × SOKKIA FX201)
 *
 * MVP 범위: Bluetooth SPP 통신, SDR33/GTS/CSV 파싱, 측정 표시 및 CSV 내보내기.
 * 좌표 정합(Kabsch)·이동측설(Helmert 2D) 수학은 geometry 패키지에 선반영.
 */
class OmegaApplication : Application()
