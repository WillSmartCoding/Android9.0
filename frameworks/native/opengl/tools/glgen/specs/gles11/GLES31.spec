void glDispatchCompute ( GLuint num_groups_x, GLuint num_groups_y, GLuint num_groups_z )
void glDispatchComputeIndirect ( GLintptr indirect )
void glDrawArraysIndirect ( GLenum mode, const void *indirect )
void glDrawElementsIndirect ( GLenum mode, GLenum type, const void *indirect )
void glFramebufferParameteri ( GLenum target, GLenum pname, GLint param )
void glGetFramebufferParameteriv ( GLenum target, GLenum pname, GLint *params )
void glGetProgramInterfaceiv ( GLuint program, GLenum programInterface, GLenum pname, GLint *params )
GLuint glGetProgramResourceIndex ( GLuint program, GLenum programInterface, const GLchar *name )
void glGetProgramResourceName ( GLuint program, GLenum programInterface, GLuint index, GLsizei bufSize, GLsizei *length, GLchar *name )
void glGetProgramResourceiv ( GLuint program, GLenum programInterface, GLuint index, GLsizei propCount, const GLenum *props, GLsizei bufSize, GLsizei *length, GLint *params )
GLint glGetProgramResourceLocation ( GLuint program, GLenum programInterface, const GLchar *name )
void glUseProgramStages ( GLuint pipeline, GLbitfield stages, GLuint program )
void glActiveShaderProgram ( GLuint pipeline, GLuint program )
GLuint glCreateShaderProgramv ( GLenum type, GLsizei count, const GLchar *const *strings )
void glBindProgramPipeline ( GLuint pipeline )
void glDeleteProgramPipelines ( GLsizei n, const GLuint *pipelines )
void glGenProgramPipelines ( GLsizei n, GLuint *pipelines )
GLboolean glIsProgramPipeline ( GLuint pipeline )
void glGetProgramPipelineiv ( GLuint pipeline, GLenum pname, GLint *params )
void glProgramUniform1i ( GLuint program, GLint location, GLint v0 )
void glProgramUniform2i ( GLuint program, GLint location, GLint v0, GLint v1 )
void glProgramUniform3i ( GLuint program, GLint location, GLint v0, GLint v1, GLint v2 )
void glProgramUniform4i ( GLuint program, GLint location, GLint v0, GLint v1, GLint v2, GLint v3 )
void glProgramUniform1ui ( GLuint program, GLint location, GLuint v0 )
void glProgramUniform2ui ( GLuint program, GLint location, GLuint v0, GLuint v1 )
void glProgramUniform3ui ( GLuint program, GLint location, GLuint v0, GLuint v1, GLuint v2 )
void glProgramUniform4ui ( GLuint program, GLint location, GLuint v0, GLuint v1, GLuint v2, GLuint v3 )
void glProgramUniform1f ( GLuint program, GLint location, GLfloat v0 )
void glProgramUniform2f ( GLuint program, GLint location, GLfloat v0, GLfloat v1 )
void glProgramUniform3f ( GLuint program, GLint location, GLfloat v0, GLfloat v1, GLfloat v2 )
void glProgramUniform4f ( GLuint program, GLint location, GLfloat v0, GLfloat v1, GLfloat v2, GLfloat v3 )
void glProgramUniform1iv ( GLuint program, GLint location, GLsizei count, const GLint *value )
void glProgramUniform2iv ( GLuint program, GLint location, GLsizei count, const GLint *value )
void glProgramUniform3iv ( GLuint program, GLint location, GLsizei count, const GLint *value )
void glProgramUniform4iv ( GLuint program, GLint location, GLsizei count, const GLint *value )
void glProgramUniform1uiv ( GLuint program, GLint location, GLsizei count, const GLuint *value )
void glProgramUniform2uiv ( GLuint program, GLint location, GLsizei count, const GLuint *value )
void glProgramUniform3uiv ( GLuint program, GLint location, GLsizei count, const GLuint *value )
void glProgramUniform4uiv ( GLuint program, GLint location, GLsizei count, const GLuint *value )
void glProgramUniform1fv ( GLuint program, GLint location, GLsizei count, const GLfloat *value )
void glProgramUniform2fv ( GLuint program, GLint location, GLsizei count, const GLfloat *value )
void glProgramUniform3fv ( GLuint program, GLint location, GLsizei count, const GLfloat *value )
void glProgramUniform4fv ( GLuint program, GLint location, GLsizei count, const GLfloat *value )
void glProgramUniformMatrix2fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix3fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix4fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix2x3fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix3x2fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix2x4fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix4x2fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix3x4fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glProgramUniformMatrix4x3fv ( GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
void glValidateProgramPipeline ( GLuint pipeline )
void glGetProgramPipelineInfoLog ( GLuint pipeline, GLsizei bufSize, GLsizei *length, GLchar *infoLog )
void glBindImageTexture ( GLuint unit, GLuint texture, GLint level, GLboolean layered, GLint layer, GLenum access, GLenum format )
void glGetBooleani_v ( GLenum target, GLuint index, GLboolean *data )
void glMemoryBarrier ( GLbitfield barriers )
void glMemoryBarrierByRegion ( GLbitfield barriers )
void glTexStorage2DMultisample ( GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height, GLboolean fixedsamplelocations )
void glGetMultisamplefv ( GLenum pname, GLuint index, GLfloat *val )
void glSampleMaski ( GLuint maskNumber, GLbitfield mask )
void glGetTexLevelParameteriv ( GLenum target, GLint level, GLenum pname, GLint *params )
void glGetTexLevelParameterfv ( GLenum target, GLint level, GLenum pname, GLfloat *params )
void glBindVertexBuffer ( GLuint bindingindex, GLuint buffer, GLintptr offset, GLsizei stride )
void glVertexAttribFormat ( GLuint attribindex, GLint size, GLenum type, GLboolean normalized, GLuint relativeoffset )
void glVertexAttribIFormat ( GLuint attribindex, GLint size, GLenum type, GLuint relativeoffset )
void glVertexAttribBinding ( GLuint attribindex, GLuint bindingindex )
void glVertexBindingDivisor ( GLuint bindingindex, GLuint divisor )
