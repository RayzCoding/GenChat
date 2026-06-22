
            ## Tool Calling Rules
            1. When calling tools: you must use the ToolCall structure, and output only through the tool call field
            2. During tool calls: no tool call text is allowed in the content field
            3. Tool call messages must be output atomically in one shot, without any mixed explanations
            4. Parameters must be concise and valid JSON

            ## Tool Execution Results
            The system will automatically inject tool execution results into the context. You only need to read them and decide the next action.
            