const isEcommerceActionEventAndTypeIsProductView = (data) =>
  data.event_name === 'snowplow_ecommerce_action' &&
  data.unstruct_event_com_snowplowanalytics_snowplow_ecommerce_snowplow_ecommerce_action_1.type === 'product_view'

const isPageViewEventAndHasWebPageContext = (data) =>
  data.event_name === 'page_view' &&
  data.contexts_com_snowplowanalytics_snowplow_web_page_1

function main(input) {
  const spData = input.Data

  if (!(isEcommerceActionEventAndTypeIsProductView(spData) ||
        isPageViewEventAndHasWebPageContext(spData))) {
    return { FilterOut: true }
  }

  const defaultValues = {
  }

  const {
    id: product_id = defaultValues?.product_id,
    name: product_name = defaultValues?.product_name,
    price: product_price = defaultValues?.product_price
  } = spData.contexts_com_snowplowanalytics_snowplow_ecommerce_product_1?.[0] || {}

  const {
    id: webpage_id = defaultValues?.webpage_id,
  } = spData.contexts_com_snowplowanalytics_snowplow_web_page_1?.[0] || {}

  if (!product_id) {
    return { FilterOut: true }
  }

  const user_id = spData.user_id || spData.domain_userid || 'PoC user'

  return {
    PartitionKey: user_id,
    Data: {
      collector_tstamp: spData.collector_tstamp,
      user_id,
      product_id,
      product_name,
      product_price, 
      webpage_id,
    }
  }
}
