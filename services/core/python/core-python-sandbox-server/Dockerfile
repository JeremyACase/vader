FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY core_python_sandbox_server ./core_python_sandbox_server

# Matches the runAsUser the Deployment's pod security context sets for this container.
RUN mkdir -p /workspace && chown 1000:1000 /workspace /app
ENV SANDBOX_WORKSPACE_DIR=/workspace
USER 1000

EXPOSE 8888

CMD ["uvicorn", "core_python_sandbox_server.main:app", "--host", "0.0.0.0", "--port", "8888"]
