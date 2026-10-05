# Localization

FactoryScope keeps user-facing text in UTF-8 bundles under `assets/bundles/`. The default `bundle.properties` is the English source of truth. Locale bundles use Mindustry's standard suffixes: `bundle_pt_BR.properties`, `bundle_ru.properties`, `bundle_zh_CN.properties`, `bundle_ko.properties`, and `bundle_es.properties`.

The Wave 1 terminology was checked against Mindustry v160.5's official locale bundles: [Russian](https://github.com/Anuken/Mindustry/blob/v160.5/core/assets/bundles/bundle_ru.properties), [Simplified Chinese](https://github.com/Anuken/Mindustry/blob/v160.5/core/assets/bundles/bundle_zh_CN.properties), [Korean](https://github.com/Anuken/Mindustry/blob/v160.5/core/assets/bundles/bundle_ko.properties), and [Spanish](https://github.com/Anuken/Mindustry/blob/v160.5/core/assets/bundles/bundle_es.properties). Those files are terminology references only; FactoryScope does not include copies of Mindustry's bundles.

Each Wave 1 locale received a semantic translation pass followed by a consistency and naturalness review against the English source, this glossary, and the in-game wording. The translations have not been independently certified by professional translators or external native-language reviewers.

The English source was not broadly rewritten. Two wording changes were made during localization review: the Area Diagnostics issue note was shortened and explicitly line-broken after 1280×720, 2× UI-scale captures showed clipping, and the Network note now describes structural item connections. Neither change adds a product claim or changes behavior.

## Mindustry terms

Use Mindustry's localized names for its blocks and resources rather than creating alternate names in FactoryScope.

| English concept | ru | zh_CN | ko | es |
| --- | --- | --- | --- | --- |
| Conveyor | Конвейер | 传送带 | 컨베이어 | Cinta transportadora |
| Armored Conveyor | Бронированный конвейер | 装甲传送带 | 장갑 컨베이어 | Cinta transportadora acorazada |
| Duct | Предметный канал | 物品管道 | 도관 | Conducto |
| Armored Duct | Защищённый предметный канал | 装甲管道 | 장갑 도관 | Conducto acorazado |
| Router | Маршрутизатор | 路由器 | 분배기 | Enrutador |
| Junction | Перекрёсток | 交叉器 | 교차기 | Cruce |
| Sorter | Сортировщик | 分类器 | 필터 | Clasificador |
| Conduit | Трубопровод | 导管 | 파이프 | Tubería |
| Liquid Router | Жидкостный маршрутизатор | 流体路由器 | 액체 분배기 | Enrutador de líquidos |
| Battery | Аккумулятор | 电池 | 배터리 | Batería |
| Power Node | Силовой узел | 电力节点 | 전력 노드 | Nodo de energía |
| Sand | Песок | 沙 | 모래 | Arena |
| Copper | Медь | 铜 | 구리 | Cobre |
| Lead | Свинец | 铅 | 납 | Plomo |
| Water | Вода | 水 | 물 | Agua |
| Oil | Нефть | 石油 | 석유 | Petróleo |
| Cryofluid | Криогенная жидкость | 冷冻液 | 냉각수 | Líquido criogénico |

## FactoryScope concepts

These terms describe FactoryScope's evidence, not additional Mindustry mechanics. Keep their wording conservative: a structural route is not observed flow, a reachable producer is not proof of sufficient supply, and an incomplete trace is not a dead end.

| English concept | ru | zh_CN | ko | es |
| --- | --- | --- | --- | --- |
| Network (item topology) | Сеть предметов | 物品网络 | 아이템 네트워크 | Red de objetos |
| Supply Trace | Трассировка снабжения | 供应追踪 | 공급 추적 | Rastreo de suministro |
| Snapshot (captured evidence) | Снимок данных | 证据快照 | 증거 스냅샷 | Instantánea de la evidencia |
| Structural route | Структурный маршрут | 结构路径 | 구조적 경로 | Ruta estructural |
| Boundary continuation | Продолжение за границей области | 选区边界之外的延续 | 영역 경계 밖으로 이어짐 | Continuación fuera del área |
| Partial / incomplete topology | Неполная топология | 不完整的拓扑 | 불완전한 토폴로지 | Topología incompleta |
| Unsupported / unmodeled transport | Немоделируемый транспорт | 未建模的运输 | 모델링되지 않은 운송 | Transporte no modelado |
| Structural dead end | Структурный тупик | 结构死路 | 구조적 막다른 경로 | Callejón sin salida estructural |
| Producer | Производитель | 生产者 | 생산자 | Productor |
| Consumer | Потребитель | 消耗者 | 소비자 | Consumidor |
| Storage | Хранилище | 储存 | 저장소 | Almacenamiento |
| Current demand | Текущая потребность | 当前需求 | 현재 전력 수요 | Demanda actual |
| Generation | Генерация | 发电量 | 발전량 | Generación |
| Satisfaction (power demand) | Покрытие потребности | 需求满足率 | 전력 수요 충족률 | Cobertura de la demanda |

`FactoryScope`, `PowerScope`, and `LiquidScope` are product names and remain unchanged. `PowerGraph` is an engine identifier; explanatory text may describe it as the complete Mindustry power graph, but must not turn graph membership into a claim about per-wire power flow.

## Validation

`BundleTest` checks that every locale has exactly the English key set, that placeholder indices and multiplicities match, values are nonempty, malformed or raw-key values are absent, and files decode as UTF-8. FactoryScope bundle values use only plain decimal argument tokens such as `{0}` and `{1}`; the validator requires each token and repetition count to match English, while allowing their order to change. Braces are reserved for these tokens rather than literal text. Each test run writes a machine-readable inventory to `build/reports/localization/bundle-inventory.tsv`.

Real-client acceptance is selected with Gradle's `-Plocale` property. It checks the resolved Mindustry locale and a localized FactoryScope sentinel in addition to running the same product scenarios in every locale. Locale strings are project translations aligned with Mindustry terminology; this documentation does not claim official or professional translation certification.
