let viewState = {};

function process() {
  try {
    // Usa a função oficial do Redpanda Connect para acessar os dados estruturados
    const rawEvent = benthos.v0_msg_as_structured();
    console.log("Raw event:", JSON.stringify(rawEvent));
    
    // Tenta fazer parse se for string
    const data = typeof rawEvent === 'string' ? JSON.parse(rawEvent) : rawEvent;
    console.log("Parsed data:", JSON.stringify(data));
    
    if (!data.event_name) {
      console.log("Skipping event without event_name. Available fields:", Object.keys(data));
      return null;
    }
    
    const userId = data.user_id;
    const webpageId = data.webpage_id;
    const timestamp = new Date(data.collector_tstamp);
    
    if (data.event_name === 'snowplow_ecommerce_action') {
      console.log("New product view: User=" + userId + ", Product=" + data.product_id + ", Webpage=" + webpageId);
      viewState[userId] = {
        webpageId: webpageId,
        startTime: timestamp,
        productId: data.product_id,
        lastPing: timestamp
      };
      return null;
    }
    
    if (data.event_name === 'page_ping' && 
        viewState[userId] && 
        viewState[userId].webpageId === webpageId) {
      
      viewState[userId].lastPing = timestamp;
      const duration = (timestamp - viewState[userId].startTime) / 1000;
      
      console.log("Page ping: User=" + userId + ", Duration=" + duration + "s, Webpage=" + webpageId);
      
      if (duration >= 90) {
        console.log("Generating discount: User=" + userId + ", Product=" + viewState[userId].productId);
        
        const result = {
          schema: "iglu:com.snowplow/shopper_discount_applied/jsonschema/1-0-0",
          data: {
            shopper_id: userId,
            product_id: viewState[userId].productId,
            discount: 0.1
          }
        };
        
        delete viewState[userId];
        console.log("State cleared for user " + userId);
        
        return result;
      }
    }
    
    return null;
  } catch (error) {
    console.error("Error processing event:", error);
    console.error("Stack trace:", error.stack);
    return null;
  }
}

root = process();
