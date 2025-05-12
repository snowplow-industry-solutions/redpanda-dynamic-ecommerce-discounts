const isEcommerceActionEventAndTypeIsProductView = (data) =>
  data.event_name === 'snowplow_ecommerce_action' &&
  data.unstruct_event_com_snowplowanalytics_snowplow_ecommerce_snowplow_ecommerce_action_1.type === 'product_view'

const isPagePingEvent = (data) =>
  data.event_name === 'page_ping'

function main(input) {
  const spData = input.Data
  const _isPagePingEvent = isPagePingEvent(spData)

  if (!(isEcommerceActionEventAndTypeIsProductView(spData) || _isPagePingEvent)) {
    return { FilterOut: true }
  }

  const {
    id: product_id,
    name: product_name,
    price: product_price
  } = spData.contexts_com_snowplowanalytics_snowplow_ecommerce_product_1?.[0] || {}

  const {
    id: webpage_id
  } = spData.contexts_com_snowplowanalytics_snowplow_web_page_1?.[0] || {}

  const user_id = spData.user_id || spData.domain_userid || 'PoC user'

  if (spData.event_name === 'snowplow_ecommerce_action') {
    spData.event_name = 'product_view'
  }

  let data = {
    event_name: spData.event_name,
    collector_tstamp: spData.collector_tstamp,
    user_id,
    product_id,
    product_name,
    product_price,
    webpage_id,
  }

  if (_isPagePingEvent) {
    const { product_id, product_name, product_price, ...newData } = data
    data = newData
  }

  return { PartitionKey: user_id, Data: data }
}
