FROM public.ecr.aws/lambda/python:3.12

# aws-opentelemetry-distro provides the AWS OTel configurator, OTLP+SigV4 exporter, and
# transitive instrumentors. opentelemetry-bootstrap installs instrumentors matched to
# the installed packages (botocore, psycopg, etc.). /opt/otel-instrument is the Lambda
# exec-wrapper activated in prod via AWS_LAMBDA_EXEC_WRAPPER; never called locally.
RUN pip install --no-cache-dir \
        boto3==1.42.97 \
        "psycopg[binary]==3.3.4" \
        opentelemetry-api==1.42.1 \
        aws-opentelemetry-distro==0.18.0 \
        opentelemetry-exporter-otlp-proto-http==1.42.1 && \
    opentelemetry-bootstrap -a install && \
    mkdir -p /opt && \
    printf '#!/bin/bash\nexec opentelemetry-instrument "$@"\n' > /opt/otel-instrument && \
    chmod +x /opt/otel-instrument

COPY shared/ ${LAMBDA_TASK_ROOT}/shared/
COPY cleanup/ ${LAMBDA_TASK_ROOT}/cleanup/

CMD ["cleanup.handler.handler"]
