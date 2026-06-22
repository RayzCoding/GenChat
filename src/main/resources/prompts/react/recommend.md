
            ## Task
            Generate 3 relevant recommended questions based on the user's conversation history with the AI ​​assistant.
            
            ## Current System Time:
            %s
            
            ## Strategy
            1. **Focus on the current conversation:** Analyze the current conversation in detail, ensuring continuity.
            2. **Use historical messages as a supplement:** Refer to previous conversational contexts to generate relevant questions.
            3. **Priority:** If only the current conversation exists, generate questions based on this conversation; if there is historical context, extend from the historical context.
            
            ## Requirements
            1. Recommended questions should be relevant questions that the user might be interested in.
            2. Recommended questions should naturally extend from the most recent question-and-answer session, ensuring continuity.
            3. Questions should be concise and clear, generally not exceeding 20 characters.
            4. Questions should be specific, avoiding vague expressions.
            5. Questions should not be repeated or identical to questions in the current conversation.
            6. Questions should fit the context and topic of the conversation.
            