# foodpacks

Dados de alimentos para o Metriq, processados de fontes abertas.

- **USDA FoodData Central** — CC0
- **Open Food Facts** — ODbL (atribuição obrigatória; dados derivados também ODbL)

O app sempre funciona com o pack base embutido. Packs extras são opcionais.

## Formato

Ver `schema/pack_format.md`.

IDs de nutrientes mapeados (em `scripts/process_usda.py`, `scripts/convert_expanded.py`
e `config/usda_nutrients.yaml`) precisam corresponder a colunas de `common.FIELDS`.
Fósforo (USDA/FDC id 1091) **não é rastreado**: o pack de 26 colunas não tem esse
campo e adicioná-lo exigiria migrar também o parser do app. Qualquer mapeamento
para um campo inexistente é descartado silenciosamente por `write_pack`; o teste
`tests/test_mapping_fields.py` guarda isso.

## Uso

```bash
python scripts/download_off.py --out data/off.csv.gz
python scripts/process_off.py --input data/off.csv.gz --out packs/off-global.tsv
python scripts/merge_packs.py --inputs packs/base.tsv packs/off-global.tsv --out packs/merged.tsv
python scripts/generate_manifest.py --packs packs --out packs/manifest.json
```

## Pack embutido expandido

Gera `core-data/src/main/assets/foods.tsv` (26 colunas) a partir de USDA FoodData
Central (Foundation / SR Legacy / Branded) e de uma amostra do Open Food Facts:

```bash
python scripts/build_expanded_pack.py
# FDC_API_KEY (ou USDA_API_KEY) substitui o DEMO_KEY padrão
```

O script deduplica por código de barras/nome, limita o pacote a ~3000 alimentos
básicos + ~200 produtos com barcode, e **só substitui** `foods.tsv` se a geração
completa e a validação de tamanho (<2 MB) passarem. `scripts/convert_expanded.py`
contém apenas funções puras, testadas sem rede.

Testes: `python -m pytest tests -q`
