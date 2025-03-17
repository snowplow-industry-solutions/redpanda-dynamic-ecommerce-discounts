function main(input) {
  const spData = input.Data

  if (!(spData.event_name === 'snowplow_ecommerce_action' &&
        spData.unstruct_event_com_snowplowanalytics_snowplow_ecommerce_snowplow_ecommerce_action_1)) {
    return { FilterOut: true }
  }

  if (!(spData.unstruct_event_com_snowplowanalytics_snowplow_ecommerce_snowplow_ecommerce_action_1.type === 'product_view')) {
    return { FilterOut: true }
  }

  const defaultValues = {
    product_id: -1,
    product_name: 'Product not found',
    product_price: 0.0,
  }

  const {
    id = defaultValues.product_id,
    name = defaultValues.product_name,
    price = defaultValues.product_price
  } = spData.contexts_com_snowplowanalytics_snowplow_ecommerce_product_1?.[0] || {}

  const user_id = spData.user_id || spData.domain_userid || 'PoC user'

  return {
    PartitionKey: user_id,
    Data: {
      user_id: user_id,
      event_id: spData.event_id,
      product_id: id,
      product_name: name,
      product_price: price, 
      duration: 0,
    }
  }
}
