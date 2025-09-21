# Pose Coach Camera (skeleton)

一個「姿勢教練相機」專案骨架：端上以 MediaPipe 進行即時姿態地標；當姿態穩定時，把 33 點地標（JSON）送到 Gemini 2.5，取回**結構化**的拍照姿勢建議。

> 本骨架聚焦於核心 domain 模組與測試雛形；Android 與 iOS app 僅作佔位。請依 CLAUDE.md 的 Sprint 指南用 **Claude Code CLI** 漸進補齊。

## 子模組
- `core-geom/`：幾何與平滑（含 OneEuroFilter 簡化版）
- `core-pose/`：33 點定義、骨架拓撲、姿態穩定 gate
- `suggestions-api/`：Gemini 2.5 結構化輸出 schema 與 client 介面

## 快速開始（建議流程）
1. 以 Claude Code 建立 P1 任務（見 `.claude/tasks/sprint-P1.yaml`）。
2. 在 `core-geom` 與 `core-pose` 跑測試（本骨架使用 JUnit5 + Truth）。
3. 將 `suggestions-api` 的 fake client 換成真實 Gemini 2.5 呼叫（以環境變數注入 API Key）。
4. 在 `app/` 加入 CameraX + Overlay（或 OverlayEffect），將 normalized→pixel 轉換打通後手動驗收。

## 授權
MIT
