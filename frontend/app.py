import time

import requests
import streamlit as st

st.set_page_config(page_title="本地知识库问答", layout="wide")

st.markdown(
    """
    <style>
    @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;600&family=IBM+Plex+Sans:wght@400;600&display=swap');
    html, body, [class*="css"] { font-family: "IBM Plex Sans", sans-serif; }
    .stApp { background: #0f1216; color: #e6e9ef; }
    h1, h2, h3 { font-family: "IBM Plex Mono", monospace; letter-spacing: 0.5px; }
    .stButton>button { background: #1f6feb; color: #fff; border-radius: 8px; border: none; }
    .stTextInput>div>div>input { background: #151a21; color: #e6e9ef; }
    .stFileUploader { background: #151a21; padding: 12px; border-radius: 8px; }
    </style>
    """,
    unsafe_allow_html=True
)

st.title("本地知识库问答")

tabs = st.tabs(["上传入库", "问答检索"])

with tabs[0]:
    st.subheader("上传文档并入库")
    upload_version = st.text_input("版本标识（可选）", "v1", key="upload_version")
    upload_tags = st.text_input("标签（逗号分隔，可选）", "guide,api", key="upload_tags")
    upload_source = st.text_input("来源标识（可选）", "upload", key="upload_source")
    retry_times = st.number_input("失败重试次数", min_value=0, max_value=3, value=1, step=1)
    files = st.file_uploader(
        "选择文件（支持 pdf/md/markdown/txt/log/csv/docx/html/htm）",
        type=["pdf", "md", "markdown", "txt", "log", "csv", "docx", "html", "htm"],
        accept_multiple_files=True
    )
    # 上传并触发入库
    if st.button("上传并入库"):
        if not files:
            st.warning("请先选择至少一个文件")
        else:
            data = {}
            if upload_version.strip():
                data["version"] = upload_version.strip()
            if upload_tags.strip():
                data["tags"] = upload_tags.strip()
            if upload_source.strip():
                data["source"] = upload_source.strip()

            total = len(files)
            progress = st.progress(0)
            status = st.empty()
            success_files = 0
            failed_files = []
            ingested_total = 0
            stored_total = 0
            stored_paths = []

            for idx, item in enumerate(files, start=1):
                status.info(f"处理中 {idx}/{total}：{item.name}")
                ok = False
                attempt = 0
                while attempt <= retry_times and not ok:
                    try:
                        resp = requests.post(
                            "http://localhost:8080/api/ingest/upload",
                            files=[("files", (item.name, item.getvalue(), item.type or "application/octet-stream"))],
                            data=data,
                            timeout=300
                        )
                        resp.raise_for_status()
                        result = resp.json()
                        ingested_total += int(result.get("ingested") or 0)
                        stored_total += int(result.get("stored") or 0)
                        stored_paths.extend(result.get("storedPaths", []))
                        ok = True
                    except Exception:
                        attempt += 1
                        if attempt <= retry_times:
                            time.sleep(1)
                if ok:
                    success_files += 1
                else:
                    failed_files.append(item.name)
                progress.progress(idx / total)

            st.success("批次处理完成")
            col1, col2, col3, col4 = st.columns(4)
            col1.metric("成功文件", success_files)
            col2.metric("失败文件", len(failed_files))
            col3.metric("入库文档", ingested_total)
            col4.metric("存储文件", stored_total)

            if stored_paths:
                st.subheader("入库路径")
                for path in stored_paths:
                    st.write("- " + path)

            if failed_files:
                st.subheader("失败文件")
                for name in failed_files:
                    st.write("- " + name)

with tabs[1]:
    st.subheader("问答检索")
    question = st.text_input("请输入问题", key="query_question")
    version = st.text_input("版本过滤（可选）", "v1", key="query_version")
    keywords = st.text_input("关键词增强（逗号分隔，可选）", "", key="query_keywords")

    if st.button("查询", key="query_btn"):
        payload = {"question": question}
        if version.strip():
            payload["version"] = version.strip()
        if keywords.strip():
            payload["keywords"] = [k.strip() for k in keywords.split(",") if k.strip()]

        resp = requests.post("http://localhost:8080/api/qa", json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()

        st.subheader("答案")
        st.write(data.get("answer"))

        st.subheader("证据片段")
        for item in data.get("evidence", []):
            st.write("- " + item)

        st.subheader("来源")
        for item in data.get("sources", []):
            st.write("- " + item)
