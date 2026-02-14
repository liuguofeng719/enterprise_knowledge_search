import os
import tempfile
from typing import Dict, List, Optional

import requests
from bs4 import BeautifulSoup
from fastapi import FastAPI, File, Form, UploadFile
from llama_index.core import Document, Settings, StorageContext, VectorStoreIndex
from llama_index.core.node_parser import SentenceSplitter
from llama_index.embeddings.huggingface import HuggingFaceEmbedding
from pydantic import BaseModel


try:
    from llama_index.core import SimpleDirectoryReader
except ImportError:
    from llama_index.readers.file import SimpleDirectoryReader


app = FastAPI()
_SETTINGS_READY = False


def init_settings() -> None:
    global _SETTINGS_READY
    if _SETTINGS_READY:
        return
    embed_model = os.getenv("LLAMAINDEX_EMBED_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
    chunk_size = int(os.getenv("LLAMAINDEX_CHUNK_SIZE", "800"))
    chunk_overlap = int(os.getenv("LLAMAINDEX_CHUNK_OVERLAP", "120"))
    Settings.embed_model = HuggingFaceEmbedding(model_name=embed_model)
    Settings.node_parser = SentenceSplitter(chunk_size=chunk_size, chunk_overlap=chunk_overlap)
    Settings.llm = None
    _SETTINGS_READY = True


def build_vector_store():
    import chromadb
    from llama_index.vector_stores.chroma import ChromaVectorStore

    host = os.getenv("CHROMA_HOST", "localhost")
    port = int(os.getenv("CHROMA_PORT", "8000"))
    tenant = os.getenv("CHROMA_TENANT", "default_tenant")
    database = os.getenv("CHROMA_DATABASE", "default_database")
    collection = os.getenv("LLAMAINDEX_COLLECTION", "llamaindex_v1")
    client = chromadb.HttpClient(host=host, port=port, tenant=tenant, database=database)
    chroma_collection = client.get_or_create_collection(collection)
    return ChromaVectorStore(chroma_collection=chroma_collection)


def build_storage_context() -> StorageContext:
    init_settings()
    vector_store = build_vector_store()
    return StorageContext.from_defaults(vector_store=vector_store)


def as_filters(filters: Optional["QueryFilters"]) -> Dict[str, str]:
    if filters is None:
        return {}
    result: Dict[str, str] = {}
    if filters.version:
        result["version"] = filters.version
    if filters.source:
        result["source"] = filters.source
    if filters.tags:
        result["tags"] = ",".join(filters.tags)
    return result


class QueryFilters(BaseModel):
    version: Optional[str] = None
    tags: Optional[List[str]] = None
    source: Optional[str] = None


class QueryRequest(BaseModel):
    question: str
    topK: Optional[int] = None
    filters: Optional[QueryFilters] = None


class QueryItem(BaseModel):
    text: str
    score: float
    metadata: Dict[str, str]


class QueryResponse(BaseModel):
    items: List[QueryItem]


class IngestUrlsRequest(BaseModel):
    urls: List[str]
    version: Optional[str] = None
    tags: Optional[List[str]] = None
    source: Optional[str] = None


class IngestResponse(BaseModel):
    ingested: int
    stored: int
    failed: List[str]


@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "ok"}


@app.post("/ingest", response_model=IngestResponse)
def ingest(
    files: List[UploadFile] = File(...),
    version: Optional[str] = Form(None),
    tags: Optional[str] = Form(None),
    source: Optional[str] = Form(None),
) -> IngestResponse:
    storage_context = build_storage_context()
    ingested: List[Document] = []
    failed: List[str] = []
    tag_list = [t.strip() for t in tags.split(",")] if tags else []

    with tempfile.TemporaryDirectory() as tmpdir:
        for upload in files:
            if upload is None:
                continue
            filename = upload.filename or "upload"
            file_path = os.path.join(tmpdir, filename)
            with open(file_path, "wb") as handle:
                handle.write(upload.file.read())
            metadata = {
                "version": version or "",
                "tags": ",".join(tag_list),
                "source": source or "upload",
                "path": filename,
            }
            try:
                reader = SimpleDirectoryReader(input_files=[file_path], file_metadata=lambda _: metadata)
                ingested.extend(reader.load_data())
            except Exception:
                failed.append(filename)

    if not ingested:
        return IngestResponse(ingested=0, stored=0, failed=failed)

    VectorStoreIndex.from_documents(ingested, storage_context=storage_context)
    return IngestResponse(ingested=len(ingested), stored=len(ingested), failed=failed)


@app.post("/ingest/urls", response_model=IngestResponse)
def ingest_urls(request: IngestUrlsRequest) -> IngestResponse:
    storage_context = build_storage_context()
    ingested: List[Document] = []
    failed: List[str] = []
    tag_list = request.tags or []
    for url in request.urls:
        try:
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            soup = BeautifulSoup(response.text, "html.parser")
            text = soup.get_text(separator="\n")
            metadata = {
                "version": request.version or "",
                "tags": ",".join(tag_list),
                "source": request.source or "web",
                "path": url,
            }
            ingested.append(Document(text=text, metadata=metadata))
        except Exception:
            failed.append(url)

    if not ingested:
        return IngestResponse(ingested=0, stored=0, failed=failed)

    VectorStoreIndex.from_documents(ingested, storage_context=storage_context)
    return IngestResponse(ingested=len(ingested), stored=len(ingested), failed=failed)


@app.post("/query", response_model=QueryResponse)
def query(request: QueryRequest) -> QueryResponse:
    storage_context = build_storage_context()
    index = VectorStoreIndex.from_vector_store(storage_context.vector_store, storage_context=storage_context)
    top_k = request.topK or int(os.getenv("LLAMAINDEX_TOP_K", "5"))
    candidate_size = int(os.getenv("LLAMAINDEX_CANDIDATE_SIZE", str(max(top_k * 3, top_k))))
    retriever = index.as_retriever(similarity_top_k=candidate_size)
    nodes = retriever.retrieve(request.question)

    raw_items: List[QueryItem] = []
    for node in nodes:
        metadata = {str(k): str(v) for k, v in (node.metadata or {}).items()}
        score = node.score if node.score is not None else 0.0
        raw_items.append(QueryItem(text=node.get_content(), score=float(score), metadata=metadata))

    filtered: List[QueryItem] = []
    filters = request.filters
    for item in raw_items:
        if filters and filters.version and item.metadata.get("version") != filters.version:
            continue
        if filters and filters.source and item.metadata.get("source") != filters.source:
            continue
        if filters and filters.tags:
            stored_tags = {t.strip() for t in item.metadata.get("tags", "").split(",") if t.strip()}
            if not set(filters.tags).issubset(stored_tags):
                continue
        filtered.append(item)
        if len(filtered) >= top_k:
            break

    return QueryResponse(items=filtered)
