FROM public.ecr.aws/lambda/python:3.12

RUN pip install --no-cache-dir boto3 "psycopg[binary]"

COPY shared/ ${LAMBDA_TASK_ROOT}/shared/
COPY outbox_poller/ ${LAMBDA_TASK_ROOT}/outbox_poller/

CMD ["outbox_poller.handler.handler"]
